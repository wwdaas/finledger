package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskLevel;
import com.finledger.risk.model.RiskPhase;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

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
    public String name() {
        return "High amount transaction";
    }

    @Override
    public RiskPhase phase() {
        return RiskPhase.IN_TRANSACTION;
    }

    @Override
    public RiskEvaluation evaluate(RiskContext context) {
        BigDecimal reviewThreshold = properties.getHighAmountReviewThreshold();
        BigDecimal rejectThreshold = properties.getHighAmountRejectThreshold();
        Map<String, Object> metadata = Map.of(
                "amount", context.amount(),
                "reviewThreshold", reviewThreshold,
                "rejectThreshold", rejectThreshold,
                "currency", "CNY"
        );
        if (context.amount().compareTo(reviewThreshold) < 0) {
            return RiskEvaluation.pass(CODE, name(), metadata);
        }
        if (context.amount().compareTo(rejectThreshold) < 0) {
            return new RiskEvaluation(
                    CODE, name(), RiskLevel.MEDIUM, RiskDecision.REVIEW,
                    "Transaction amount " + context.amount().toPlainString()
                            + " reached review threshold " + reviewThreshold.toPlainString(),
                    metadata
            );
        }
        return new RiskEvaluation(
                CODE, name(), RiskLevel.HIGH, RiskDecision.REJECT,
                "Transaction amount " + context.amount().toPlainString()
                        + " reached reject threshold " + rejectThreshold.toPlainString(),
                metadata
        );
    }
}
