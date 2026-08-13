package com.finledger.ai.service;

import com.finledger.ai.dto.AiRiskExplanationResponse;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.risk.service.RiskEventQueryService;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.settlement.service.DeferredTransferQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiRiskExplanationService {

    private final TransactionReferenceExtractor referenceExtractor;
    private final DeferredTransferQueryService transferQueryService;
    private final RiskEventQueryService riskEventQueryService;
    private final TransactionRiskExplanationGenerator explanationGenerator;

    public AiRiskExplanationService(
            TransactionReferenceExtractor referenceExtractor,
            DeferredTransferQueryService transferQueryService,
            RiskEventQueryService riskEventQueryService,
            TransactionRiskExplanationGenerator explanationGenerator
    ) {
        this.referenceExtractor = referenceExtractor;
        this.transferQueryService = transferQueryService;
        this.riskEventQueryService = riskEventQueryService;
        this.explanationGenerator = explanationGenerator;
    }

    public AiRiskExplanationResponse explain(Long userId, String question) {
        String transactionNo = referenceExtractor.extract(question);
        DeferredTransferResponse transaction =
                transferQueryService.getOwnedByNo(userId, transactionNo);
        List<RiskEventResponse> riskEvents =
                riskEventQueryService.findByBusinessNo(userId, transactionNo);
        String answer = explanationGenerator.explain(question, transaction, riskEvents);
        return new AiRiskExplanationResponse(
                transaction.transferNo(), transaction.status(), transaction.riskDecision(), answer,
                transaction.totalBalance(), transaction.availableBalance(), transaction.frozenBalance(),
                riskEvents
        );
    }
}
