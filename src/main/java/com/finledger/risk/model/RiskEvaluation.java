package com.finledger.risk.model;

public record RiskEvaluation(
        String ruleCode,
        RiskLevel riskLevel,
        RiskDecision decision,
        String reason
) {

    public static RiskEvaluation pass(String ruleCode) {
        return new RiskEvaluation(ruleCode, RiskLevel.LOW, RiskDecision.PASS, "Rule not triggered");
    }

    public boolean triggered() {
        return decision != RiskDecision.PASS;
    }
}
