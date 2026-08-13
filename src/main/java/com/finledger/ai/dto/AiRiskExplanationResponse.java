package com.finledger.ai.dto;

import com.finledger.risk.dto.RiskEventResponse;

import java.math.BigDecimal;
import java.util.List;

public record AiRiskExplanationResponse(
        String transactionNo,
        String status,
        String riskDecision,
        String answer,
        BigDecimal totalBalance,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        List<RiskEventResponse> riskEvents
) {
}
