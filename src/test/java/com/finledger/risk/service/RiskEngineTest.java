package com.finledger.risk.service;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskLevel;
import com.finledger.risk.model.RiskPhase;
import com.finledger.risk.rule.RiskRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineTest {

    @Test
    void shouldApplyOnlyRequestedPhaseAndUseMostRestrictiveDecision() {
        RiskRule review = rule("REVIEW_RULE", RiskPhase.IN_TRANSACTION, RiskDecision.REVIEW);
        RiskRule reject = rule("REJECT_RULE", RiskPhase.IN_TRANSACTION, RiskDecision.REJECT);
        RiskRule pre = rule("PRE_RULE", RiskPhase.PRE_TRANSACTION, RiskDecision.REVIEW);
        RiskEngine engine = new RiskEngine(List.of(review, reject, pre), new RiskProperties());

        var assessment = engine.evaluate(context(), RiskPhase.IN_TRANSACTION);

        assertThat(assessment.decision()).isEqualTo(RiskDecision.REJECT);
        assertThat(assessment.triggeredRules()).extracting(RiskEvaluation::ruleCode)
                .containsExactly("REVIEW_RULE", "REJECT_RULE");
    }

    private RiskRule rule(String code, RiskPhase phase, RiskDecision decision) {
        return new RiskRule() {
            @Override public String code() { return code; }
            @Override public RiskPhase phase() { return phase; }
            @Override public RiskEvaluation evaluate(RiskContext context) {
                return new RiskEvaluation(code, RiskLevel.HIGH, decision, "triggered");
            }
        };
    }

    private RiskContext context() {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal("1.00"), LocalDateTime.now()
        );
    }
}
