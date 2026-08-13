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
    void shouldReviewOnlyAmountsAboveConfiguredThreshold() {
        RiskProperties properties = new RiskProperties();
        properties.setHighAmountThreshold(new BigDecimal("50000.00"));
        HighAmountRiskRule rule = new HighAmountRiskRule(properties);

        assertThat(rule.evaluate(context("50000.00")).decision()).isEqualTo(RiskDecision.PASS);
        assertThat(rule.evaluate(context("50000.01")).decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(rule.evaluate(context("50000.01")).ruleCode()).isEqualTo("HIGH_AMOUNT");
    }

    private RiskContext context(String amount) {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal(amount), LocalDateTime.now()
        );
    }
}
