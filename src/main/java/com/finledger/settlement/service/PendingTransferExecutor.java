package com.finledger.settlement.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountNotActiveException;
import com.finledger.account.exception.InsufficientAvailableBalanceException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.account.service.LockedTransferAccounts;
import com.finledger.common.money.MoneyAmounts;
import com.finledger.risk.model.RiskAssessment;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskPhase;
import com.finledger.risk.service.RiskEngine;
import com.finledger.risk.service.RiskEventRecorder;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.exception.CurrencyMismatchException;
import com.finledger.transfer.mapper.TransferOrderMapper;
import com.finledger.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PendingTransferExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingTransferExecutor.class);

    private final UserMapper userMapper;
    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final FundMovementRecorder movementRecorder;
    private final RiskEngine riskEngine;
    private final RiskEventRecorder riskEventRecorder;

    public PendingTransferExecutor(
            UserMapper userMapper,
            AccountService accountService,
            AccountMapper accountMapper,
            TransferOrderMapper transferOrderMapper,
            FundMovementRecorder movementRecorder,
            RiskEngine riskEngine,
            RiskEventRecorder riskEventRecorder
    ) {
        this.userMapper = userMapper;
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.transferOrderMapper = transferOrderMapper;
        this.movementRecorder = movementRecorder;
        this.riskEngine = riskEngine;
        this.riskEventRecorder = riskEventRecorder;
    }

    @Transactional
    public PendingTransferOutcome execute(
            Long userId,
            TransferRequest request,
            BigDecimal amount,
            String transferNo,
            LocalDateTime occurredAt,
            RiskAssessment preAssessment
    ) {
        if (userMapper.selectByIdForUpdate(userId) == null) {
            throw new IllegalStateException("Authenticated user no longer exists");
        }
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

        TransferOrderEntity order = createProcessingOrder(
                userId, request, amount, transferNo, fromAccount.getCurrency(), occurredAt
        );
        RiskContext context = new RiskContext(
                userId, transferNo, "DEFERRED_TRANSFER", request.fromAccountId(),
                request.toAccountId(), amount, occurredAt
        );
        RiskAssessment assessment = preAssessment.combine(
                riskEngine.evaluate(context, RiskPhase.IN_TRANSACTION)
        );
        riskEventRecorder.record(userId, order.getId(), transferNo, amount, assessment);
        if (assessment.decision() == RiskDecision.REJECT) {
            finishRiskAssessment(order, "FAILED", assessment.decision().name(), occurredAt);
            LOGGER.warn(
                    "Risk rejected deferred transfer transferNo={} userId={} rules={}",
                    transferNo, userId,
                    assessment.triggeredRules().stream().map(event -> event.ruleCode()).toList()
            );
            return new PendingTransferOutcome(toResponse(order, fromAccount), true);
        }

        finishRiskAssessment(order, "PENDING", assessment.decision().name(), null);
        freeze(fromAccount, order, amount);
        LOGGER.info(
                "Funds frozen transferNo={} userId={} accountId={} amount={} riskDecision={}",
                transferNo, userId, fromAccount.getId(), amount, assessment.decision()
        );
        return new PendingTransferOutcome(toResponse(order, fromAccount), false);
    }

    private TransferOrderEntity createProcessingOrder(
            Long userId,
            TransferRequest request,
            BigDecimal amount,
            String transferNo,
            String currency,
            LocalDateTime createdAt
    ) {
        TransferOrderEntity order = new TransferOrderEntity();
        order.setTransferNo(transferNo);
        order.setOrderType("DEFERRED");
        order.setInitiatorUserId(userId);
        order.setFromAccountId(request.fromAccountId());
        order.setToAccountId(request.toAccountId());
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setStatus("PROCESSING");
        order.setRiskDecision("PASS");
        order.setCreatedAt(createdAt);
        if (transferOrderMapper.insert(order) != 1) {
            throw new IllegalStateException("Expected one inserted pending transfer order");
        }
        return order;
    }

    private void finishRiskAssessment(
            TransferOrderEntity order,
            String status,
            String decision,
            LocalDateTime completedAt
    ) {
        if (transferOrderMapper.completeRiskAssessment(
                order.getId(), status, decision, completedAt
        ) != 1) {
            throw new IllegalStateException("Expected one updated risk-assessed transfer order");
        }
        order.setStatus(status);
        order.setRiskDecision(decision);
        order.setCompletedAt(completedAt);
    }

    private void freeze(
            AccountEntity fromAccount,
            TransferOrderEntity order,
            BigDecimal amount
    ) {
        BigDecimal availableBefore = fromAccount.getAvailableBalance();
        BigDecimal frozenBefore = fromAccount.getFrozenBalance();
        BigDecimal totalBefore = fromAccount.getTotalBalance();
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
                sourceAccount.getTotalBalance(), sourceAccount.getAvailableBalance(),
                sourceAccount.getFrozenBalance(), order.getCreatedAt(), order.getCompletedAt()
        );
    }
}
