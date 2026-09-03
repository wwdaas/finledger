package com.finledger.freeze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountNotActiveException;
import com.finledger.account.exception.InsufficientAvailableBalanceException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.freeze.dto.FreezeRequest;
import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.entity.FundFreezeEntity;
import com.finledger.freeze.generator.FreezeNumberGenerator;
import com.finledger.freeze.mapper.FundFreezeMapper;
import com.finledger.idempotency.entity.IdempotencyRecordEntity;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.settlement.service.FundMovementRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class FreezeExecutor {

    public static final String IDEMPOTENCY_BUSINESS_TYPE = "FUND_FREEZE";
    private static final String FROZEN_STATUS = "FROZEN";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final FundFreezeMapper freezeMapper;
    private final FreezeNumberGenerator numberGenerator;
    private final FundMovementRecorder movementRecorder;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public FreezeExecutor(
            AccountService accountService,
            AccountMapper accountMapper,
            FundFreezeMapper freezeMapper,
            FreezeNumberGenerator numberGenerator,
            FundMovementRecorder movementRecorder,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.freezeMapper = freezeMapper;
        this.numberGenerator = numberGenerator;
        this.movementRecorder = movementRecorder;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FreezeResponse execute(
            Long userId,
            Long accountId,
            String idempotencyKey,
            String requestHash,
            FreezeRequest request
    ) {
        IdempotencyRecordEntity claim = claim(userId, idempotencyKey, requestHash);
        AccountEntity account = accountService.lockOwnedAccount(userId, accountId);
        requireFreezable(account, request.amount());

        BigDecimal availableBefore = account.getAvailableBalance();
        BigDecimal frozenBefore = account.getFrozenBalance();
        BigDecimal totalBefore = account.getTotalBalance();

        if (accountMapper.moveAvailableToFrozen(accountId, request.amount()) != 1) {
            throw new InsufficientAvailableBalanceException(accountId);
        }
        account.setAvailableBalance(availableBefore.subtract(request.amount()));
        account.setFrozenBalance(frozenBefore.add(request.amount()));
        account.setVersion(account.getVersion() + 1);
        if (totalBefore.compareTo(account.getTotalBalance()) != 0) {
            throw new IllegalStateException("Freeze changed the account total balance");
        }

        FundFreezeEntity freeze = createFreeze(userId, accountId, request);
        movementRecorder.record(
                account,
                IDEMPOTENCY_BUSINESS_TYPE,
                freeze.getId(),
                "FREEZE",
                request.amount(),
                availableBefore,
                frozenBefore,
                totalBefore
        );

        FreezeResponse response = toResponse(freeze, account);
        claim.setStatus("SUCCESS");
        claim.setResourceId(freeze.getId());
        claim.setResponseSnapshot(writeSnapshot(response));
        if (idempotencyRecordMapper.updateById(claim) != 1) {
            throw new IllegalStateException("Expected one updated idempotency claim");
        }
        return response;
    }

    private IdempotencyRecordEntity claim(Long userId, String key, String requestHash) {
        IdempotencyRecordEntity claim = new IdempotencyRecordEntity();
        claim.setUserId(userId);
        claim.setBusinessType(IDEMPOTENCY_BUSINESS_TYPE);
        claim.setIdempotencyKey(key);
        claim.setRequestHash(requestHash);
        claim.setStatus("PROCESSING");
        if (idempotencyRecordMapper.insert(claim) != 1) {
            throw new IllegalStateException("Expected one inserted idempotency claim");
        }
        return claim;
    }

    private void requireFreezable(AccountEntity account, BigDecimal amount) {
        if (!ACTIVE_STATUS.equals(account.getStatus())) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientAvailableBalanceException(account.getId());
        }
    }

    private FundFreezeEntity createFreeze(Long userId, Long accountId, FreezeRequest request) {
        FundFreezeEntity freeze = new FundFreezeEntity();
        freeze.setFreezeNo(numberGenerator.nextFreezeNo());
        freeze.setUserId(userId);
        freeze.setAccountId(accountId);
        freeze.setAmount(request.amount());
        freeze.setStatus(FROZEN_STATUS);
        freeze.setBusinessType(request.businessType());
        freeze.setRemark(request.remark());
        freeze.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (freezeMapper.insert(freeze) != 1) {
            throw new IllegalStateException("Expected one inserted fund freeze");
        }
        return freeze;
    }

    private FreezeResponse toResponse(FundFreezeEntity freeze, AccountEntity account) {
        return new FreezeResponse(
                freeze.getId(),
                freeze.getFreezeNo(),
                freeze.getAccountId(),
                freeze.getAmount(),
                freeze.getBusinessType(),
                freeze.getRemark(),
                freeze.getStatus(),
                account.getAvailableBalance(),
                account.getFrozenBalance(),
                account.getTotalBalance(),
                freeze.getCreatedAt()
        );
    }

    private String writeSnapshot(FreezeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store freeze idempotency response", exception);
        }
    }
}
