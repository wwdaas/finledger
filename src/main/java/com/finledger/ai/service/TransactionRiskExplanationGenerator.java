package com.finledger.ai.service;

import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.settlement.dto.DeferredTransferResponse;

import java.util.List;

public interface TransactionRiskExplanationGenerator {

    String explain(
            String question,
            DeferredTransferResponse transaction,
            List<RiskEventResponse> riskEvents
    );
}
