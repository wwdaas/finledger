package com.finledger.settlement.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountNotActiveException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.account.service.LockedTransferAccounts;
import com.finledger.common.money.MoneyAmounts;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.generator.TransactionRecordNumberGenerator;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.settlement.exception.TransactionNotFoundException;
import com.finledger.settlement.model.DeferredTransferStatus;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class SettlementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementService.class);

    private final TransferOrderMapper transferOrderMapper;
    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final DeferredTransferStateMachine stateMachine;
    private final FundMovementRecorder movementRecorder;
    private final TransactionRecordMapper transactionRecordMapper;
    private final TransactionRecordNumberGenerator recordNumberGenerator;

    public SettlementService(
            TransferOrderMapper transferOrderMapper,
            AccountService accountService,
            AccountMapper accountMapper,
            DeferredTransferStateMachine stateMachine,
            FundMovementRecorder movementRecorder,
            TransactionRecordMapper transactionRecordMapper,
            TransactionRecordNumberGenerator recordNumberGenerator
    ) {
        this.transferOrderMapper = transferOrderMapper;
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.stateMachine = stateMachine;
        this.movementRecorder = movementRecorder;
        this.transactionRecordMapper = transactionRecordMapper;
        this.recordNumberGenerator = recordNumberGenerator;
    }

    @Transactional
    public DeferredTransferResponse settle(Long userId, Long transferId) {
        TransferOrderEntity order = requireLockedOwnedOrder(userId, transferId);
        stateMachine.requireTransition(order.getStatus(), DeferredTransferStatus.SETTLED);
        LockedTransferAccounts locked = accountService.lockTransferAccounts(
                userId, order.getFromAccountId(), order.getToAccountId()
        );
        AccountEntity source = locked.fromAccount();
        AccountEntity target = locked.toAccount();
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new AccountNotActiveException(target.getId(), target.getStatus());
        }
        requireReservedFunds(source, order.getAmount());

        BigDecimal sourceAvailableBefore = source.getAvailableBalance();
        BigDecimal sourceFrozenBefore = source.getFrozenBalance();
        BigDecimal sourceTotalBefore = source.getBalance();
        BigDecimal targetTotalBefore = target.getBalance();
        BigDecimal targetAvailableBefore = target.getAvailableBalance();

        source.setFrozenBalance(MoneyAmounts.requireValidBalance(
                sourceFrozenBefore.subtract(order.getAmount())
        ));
        source.setBalance(MoneyAmounts.requireValidBalance(
                sourceTotalBefore.subtract(order.getAmount())
        ));
        source.setVersion(source.getVersion() + 1);
        target.setAvailableBalance(MoneyAmounts.requireValidBalance(
                targetAvailableBefore.add(order.getAmount())
        ));
        target.setBalance(MoneyAmounts.requireValidBalance(
                targetTotalBefore.add(order.getAmount())
        ));
        target.setVersion(target.getVersion() + 1);
        updateAccount(source);
        updateAccount(target);

        insertTransferRecord(
                order, source, target.getId(), "DEBIT", sourceTotalBefore, source.getBalance()
        );
        insertTransferRecord(
                order, target, source.getId(), "CREDIT", targetTotalBefore, target.getBalance()
        );
        movementRecorder.record(
                source, order.getId(), "SETTLEMENT", order.getAmount(),
                sourceAvailableBefore, sourceFrozenBefore, sourceTotalBefore
        );

        LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (transferOrderMapper.transitionPending(
                transferId, DeferredTransferStatus.SETTLED.name(), completedAt
        ) != 1) {
            throw new IllegalStateException("Pending transfer state changed during settlement");
        }
        order.setStatus(DeferredTransferStatus.SETTLED.name());
        order.setCompletedAt(completedAt);
        LOGGER.info(
                "Transfer settled transferNo={} userId={} amount={}",
                order.getTransferNo(), userId, order.getAmount()
        );
        return toResponse(order, source);
    }

    private TransferOrderEntity requireLockedOwnedOrder(Long userId, Long transferId) {
        TransferOrderEntity order = transferOrderMapper.selectByIdForUpdate(transferId);
        if (order == null || !"DEFERRED".equals(order.getOrderType())
                || !userId.equals(order.getInitiatorUserId())) {
            throw new TransactionNotFoundException(transferId);
        }
        return order;
    }

    private void requireReservedFunds(AccountEntity source, BigDecimal amount) {
        if (source.getFrozenBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Pending transfer has insufficient reserved funds");
        }
    }

    private void updateAccount(AccountEntity account) {
        if (accountMapper.updateById(account) != 1) {
            throw new IllegalStateException("Expected one updated account row: " + account.getId());
        }
    }

    private void insertTransferRecord(
            TransferOrderEntity order,
            AccountEntity account,
            Long counterpartyAccountId,
            String direction,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
        TransactionRecordEntity record = new TransactionRecordEntity();
        record.setRecordNo(recordNumberGenerator.nextRecordNo());
        record.setAccountId(account.getId());
        record.setUserId(account.getUserId());
        record.setBusinessType("TRANSFER");
        record.setBusinessId(order.getId());
        record.setDirection(direction);
        record.setAmount(order.getAmount());
        record.setCurrency(order.getCurrency());
        record.setBalanceBefore(balanceBefore);
        record.setBalanceAfter(balanceAfter);
        record.setCounterpartyAccountId(counterpartyAccountId);
        if (transactionRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("Expected one inserted settlement transaction record");
        }
    }

    private DeferredTransferResponse toResponse(
            TransferOrderEntity order,
            AccountEntity source
    ) {
        return new DeferredTransferResponse(
                order.getId(), order.getTransferNo(), order.getFromAccountId(), order.getToAccountId(),
                order.getAmount(), order.getCurrency(), order.getStatus(), order.getRiskDecision(),
                source.getBalance(), source.getAvailableBalance(), source.getFrozenBalance(),
                order.getCreatedAt(), order.getCompletedAt()
        );
    }
}
