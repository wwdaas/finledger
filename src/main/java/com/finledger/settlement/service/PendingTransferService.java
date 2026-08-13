package com.finledger.settlement.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountNotActiveException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.account.service.LockedTransferAccounts;
import com.finledger.common.money.MoneyAmounts;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.settlement.exception.InsufficientAvailableBalanceException;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.exception.CurrencyMismatchException;
import com.finledger.transfer.exception.SameAccountTransferException;
import com.finledger.transfer.generator.TransferNumberGenerator;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PendingTransferService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingTransferService.class);

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final TransferNumberGenerator transferNumberGenerator;
    private final FundMovementRecorder movementRecorder;

    public PendingTransferService(
            AccountService accountService,
            AccountMapper accountMapper,
            TransferOrderMapper transferOrderMapper,
            TransferNumberGenerator transferNumberGenerator,
            FundMovementRecorder movementRecorder
    ) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.transferOrderMapper = transferOrderMapper;
        this.transferNumberGenerator = transferNumberGenerator;
        this.movementRecorder = movementRecorder;
    }

    @Transactional
    public DeferredTransferResponse createPending(Long userId, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SameAccountTransferException();
        }
        BigDecimal amount = MoneyAmounts.requirePositive(request.amount());
        LockedTransferAccounts locked = accountService.lockTransferAccounts(
                userId, request.fromAccountId(), request.toAccountId()
        );
        AccountEntity fromAccount = locked.fromAccount();
        AccountEntity toAccount = locked.toAccount();
        requireActive(fromAccount);
        requireActive(toAccount);
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new CurrencyMismatchException();
        }
        if (fromAccount.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientAvailableBalanceException(fromAccount.getId());
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        TransferOrderEntity order = createOrder(userId, request, amount, fromAccount.getCurrency(), createdAt);
        BigDecimal availableBefore = fromAccount.getAvailableBalance();
        BigDecimal frozenBefore = fromAccount.getFrozenBalance();
        BigDecimal totalBefore = fromAccount.getBalance();
        fromAccount.setAvailableBalance(MoneyAmounts.requireValidBalance(availableBefore.subtract(amount)));
        fromAccount.setFrozenBalance(MoneyAmounts.requireValidBalance(frozenBefore.add(amount)));
        fromAccount.setVersion(fromAccount.getVersion() + 1);
        if (accountMapper.updateById(fromAccount) != 1) {
            throw new IllegalStateException("Expected one updated source account row");
        }
        movementRecorder.record(
                fromAccount, order.getId(), "FREEZE", amount,
                availableBefore, frozenBefore, totalBefore
        );
        LOGGER.info(
                "Funds frozen transferNo={} userId={} accountId={} amount={}",
                order.getTransferNo(), userId, fromAccount.getId(), amount
        );
        return toResponse(order, fromAccount);
    }

    private TransferOrderEntity createOrder(
            Long userId,
            TransferRequest request,
            BigDecimal amount,
            String currency,
            LocalDateTime createdAt
    ) {
        TransferOrderEntity order = new TransferOrderEntity();
        order.setTransferNo(transferNumberGenerator.nextTransferNo());
        order.setOrderType("DEFERRED");
        order.setInitiatorUserId(userId);
        order.setFromAccountId(request.fromAccountId());
        order.setToAccountId(request.toAccountId());
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setStatus("PENDING");
        order.setRiskDecision("PASS");
        order.setCreatedAt(createdAt);
        if (transferOrderMapper.insert(order) != 1) {
            throw new IllegalStateException("Expected one inserted pending transfer order");
        }
        return order;
    }

    private void requireActive(AccountEntity account) {
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
    }

    private DeferredTransferResponse toResponse(
            TransferOrderEntity order,
            AccountEntity sourceAccount
    ) {
        return new DeferredTransferResponse(
                order.getId(), order.getTransferNo(), order.getFromAccountId(), order.getToAccountId(),
                order.getAmount(), order.getCurrency(), order.getStatus(), order.getRiskDecision(),
                sourceAccount.getBalance(), sourceAccount.getAvailableBalance(),
                sourceAccount.getFrozenBalance(), order.getCreatedAt(), order.getCompletedAt()
        );
    }
}
