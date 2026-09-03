package com.finledger.settlement.service;

import com.finledger.common.money.MoneyAmounts;
import com.finledger.risk.exception.RiskRejectedException;
import com.finledger.risk.model.RiskAssessment;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskPhase;
import com.finledger.risk.service.RiskEngine;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.exception.SameAccountTransferException;
import com.finledger.transfer.generator.TransferNumberGenerator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PendingTransferService {

    private final TransferNumberGenerator transferNumberGenerator;
    private final RiskEngine riskEngine;
    private final PendingTransferExecutor executor;

    public PendingTransferService(
            TransferNumberGenerator transferNumberGenerator,
            RiskEngine riskEngine,
            PendingTransferExecutor executor
    ) {
        this.transferNumberGenerator = transferNumberGenerator;
        this.riskEngine = riskEngine;
        this.executor = executor;
    }

    public DeferredTransferResponse createPending(Long userId, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SameAccountTransferException();
        }
        BigDecimal amount = MoneyAmounts.requirePositive(request.amount());
        String transferNo = transferNumberGenerator.nextTransferNo();
        LocalDateTime occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        RiskContext context = new RiskContext(
                userId, transferNo, "DEFERRED_TRANSFER", request.fromAccountId(),
                request.toAccountId(), amount, occurredAt
        );
        RiskAssessment preAssessment = riskEngine.evaluate(context, RiskPhase.PRE_TRANSACTION);
        PendingTransferOutcome outcome = executor.execute(
                userId, request, amount, transferNo, occurredAt, preAssessment
        );
        if (outcome.rejected()) {
            throw new RiskRejectedException(transferNo);
        }
        return outcome.response();
    }
}
