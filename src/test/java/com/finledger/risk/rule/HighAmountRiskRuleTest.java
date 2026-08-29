package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HighAmountRiskRuleTest {

    @Test
    void shouldApplyPassReviewAndRejectBoundaries() {
        RiskProperties properties = new RiskProperties();
        properties.setHighAmountReviewThreshold(new BigDecimal("10000.00"));
        properties.setHighAmountRejectThreshold(new BigDecimal("50000.00"));
        HighAmountRiskRule rule = new HighAmountRiskRule(properties);

        assertThat(rule.evaluate(context("9999.99")).decision()).isEqualTo(RiskDecision.PASS);
        assertThat(rule.evaluate(context("10000.00")).decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(rule.evaluate(context("49999.99")).decision()).isEqualTo(RiskDecision.REVIEW);
        var rejected = rule.evaluate(context("50000.00"));
        assertThat(rejected.decision()).isEqualTo(RiskDecision.REJECT);
        assertThat(rejected.ruleCode()).isEqualTo("HIGH_AMOUNT");
        assertThat(rejected.ruleName()).isEqualTo("High amount transaction");
        assertThat(rejected.metadata()).containsKeys("amount", "reviewThreshold", "rejectThreshold");
    }

    private RiskContext context(String amount) {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal(amount), LocalDateTime.now()
        );
    }
}
