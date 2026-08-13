package com.finledger.settlement.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.common.money.MoneyAmounts;
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
public class CancellationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancellationService.class);

    private final TransferOrderMapper transferOrderMapper;
    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final DeferredTransferStateMachine stateMachine;
    private final FundMovementRecorder movementRecorder;

    public CancellationService(
            TransferOrderMapper transferOrderMapper,
            AccountService accountService,
            AccountMapper accountMapper,
            DeferredTransferStateMachine stateMachine,
            FundMovementRecorder movementRecorder
    ) {
        this.transferOrderMapper = transferOrderMapper;
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.stateMachine = stateMachine;
        this.movementRecorder = movementRecorder;
    }

    @Transactional
    public DeferredTransferResponse cancel(Long userId, Long transferId) {
        TransferOrderEntity order = requireLockedOwnedOrder(userId, transferId);
        stateMachine.requireTransition(order.getStatus(), DeferredTransferStatus.CANCELLED);
        AccountEntity source = accountService.lockOwnedAccount(userId, order.getFromAccountId());
        if (source.getFrozenBalance().compareTo(order.getAmount()) < 0) {
            throw new IllegalStateException("Pending transfer has insufficient reserved funds");
        }

        BigDecimal availableBefore = source.getAvailableBalance();
        BigDecimal frozenBefore = source.getFrozenBalance();
        BigDecimal totalBefore = source.getTotalBalance();
        source.setAvailableBalance(MoneyAmounts.requireValidBalance(
                availableBefore.add(order.getAmount())
        ));
        source.setFrozenBalance(MoneyAmounts.requireValidBalance(
                frozenBefore.subtract(order.getAmount())
        ));
        source.setVersion(source.getVersion() + 1);
        if (accountMapper.updateById(source) != 1) {
            throw new IllegalStateException("Expected one updated source account row");
        }
        movementRecorder.record(
                source, order.getId(), "UNFREEZE", order.getAmount(),
                availableBefore, frozenBefore, totalBefore
        );

        LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (transferOrderMapper.transitionPending(
                transferId, DeferredTransferStatus.CANCELLED.name(), completedAt
        ) != 1) {
            throw new IllegalStateException("Pending transfer state changed during cancellation");
        }
        order.setStatus(DeferredTransferStatus.CANCELLED.name());
        order.setCompletedAt(completedAt);
        LOGGER.info(
                "Transfer cancelled transferNo={} userId={} amount={}",
                order.getTransferNo(), userId, order.getAmount()
        );
        return new DeferredTransferResponse(
                order.getId(), order.getTransferNo(), order.getFromAccountId(), order.getToAccountId(),
                order.getAmount(), order.getCurrency(), order.getStatus(), order.getRiskDecision(),
                source.getTotalBalance(), source.getAvailableBalance(), source.getFrozenBalance(),
                order.getCreatedAt(), order.getCompletedAt()
        );
    }

    private TransferOrderEntity requireLockedOwnedOrder(Long userId, Long transferId) {
        TransferOrderEntity order = transferOrderMapper.selectByIdForUpdate(transferId);
        if (order == null || !"DEFERRED".equals(order.getOrderType())
                || !userId.equals(order.getInitiatorUserId())) {
            throw new TransactionNotFoundException(transferId);
        }
        return order;
    }
}
