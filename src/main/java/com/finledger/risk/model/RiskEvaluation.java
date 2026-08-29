package com.finledger.risk.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record RiskEvaluation(
        String ruleCode,
        String ruleName,
        RiskLevel riskLevel,
        RiskDecision decision,
        String reason,
        Map<String, Object> metadata
) {

    public RiskEvaluation {
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public RiskEvaluation(
            String ruleCode,
            RiskLevel riskLevel,
            RiskDecision decision,
            String reason
    ) {
        this(ruleCode, ruleCode, riskLevel, decision, reason, Map.of());
    }

    public static RiskEvaluation pass(
            String ruleCode,
            String ruleName,
            Map<String, Object> metadata
    ) {
        return new RiskEvaluation(
                ruleCode, ruleName, RiskLevel.LOW, RiskDecision.PASS,
                "Rule not triggered", metadata
        );
    }

    public boolean triggered() {
        return decision != RiskDecision.PASS;
    }
}
