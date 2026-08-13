package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskLevel;
import com.finledger.risk.model.RiskPhase;
import org.springframework.stereotype.Component;

@Component
public class HighAmountRiskRule implements RiskRule {

    public static final String CODE = "HIGH_AMOUNT";

    private final RiskProperties properties;

    public HighAmountRiskRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public RiskPhase phase() {
        return RiskPhase.IN_TRANSACTION;
    }

    @Override
    public RiskEvaluation evaluate(RiskContext context) {
        if (context.amount().compareTo(properties.getHighAmountThreshold()) <= 0) {
            return RiskEvaluation.pass(CODE);
        }
        return new RiskEvaluation(
                CODE,
                RiskLevel.MEDIUM,
                RiskDecision.REVIEW,
                "Transaction amount " + context.amount().toPlainString()
                        + " exceeds configured threshold "
                        + properties.getHighAmountThreshold().toPlainString()
        );
    }
}
