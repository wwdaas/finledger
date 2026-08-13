package com.finledger.risk.model;

import java.util.ArrayList;
import java.util.List;

public record RiskAssessment(
        RiskDecision decision,
        List<RiskEvaluation> triggeredRules
) {

    public RiskAssessment {
        triggeredRules = List.copyOf(triggeredRules);
    }

    public static RiskAssessment pass() {
        return new RiskAssessment(RiskDecision.PASS, List.of());
    }

    public RiskAssessment combine(RiskAssessment other) {
        List<RiskEvaluation> combined = new ArrayList<>(triggeredRules);
        combined.addAll(other.triggeredRules);
        return new RiskAssessment(
                RiskDecision.mostRestrictive(decision, other.decision),
                combined
        );
    }
}
