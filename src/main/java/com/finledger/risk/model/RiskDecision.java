package com.finledger.risk.model;

public enum RiskDecision {
    PASS(0),
    REVIEW(1),
    REJECT(2);

    private final int severity;

    RiskDecision(int severity) {
        this.severity = severity;
    }

    public static RiskDecision mostRestrictive(RiskDecision left, RiskDecision right) {
        return left.severity >= right.severity ? left : right;
    }
}
