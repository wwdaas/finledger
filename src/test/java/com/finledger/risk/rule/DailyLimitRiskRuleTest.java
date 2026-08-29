package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyLimitRiskRuleTest {

    @Test
    void shouldApplyDailyBoundariesUsingUtcDayWindow() {
        TransferOrderMapper mapper = mock(TransferOrderMapper.class);
        when(mapper.sumAcceptedOutgoing(eq(1L), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        RiskProperties properties = new RiskProperties();
        properties.setDailyReviewThreshold(new BigDecimal("30000.00"));
        properties.setDailyRejectThreshold(new BigDecimal("50000.00"));
        DailyLimitRiskRule rule = new DailyLimitRiskRule(mapper, properties);

        assertThat(rule.evaluate(context("29999.99")).decision()).isEqualTo(RiskDecision.PASS);
        assertThat(rule.evaluate(context("30000.00")).decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(rule.evaluate(context("50000.00")).decision()).isEqualTo(RiskDecision.REVIEW);
        var rejected = rule.evaluate(context("50000.01"));
        assertThat(rejected.decision()).isEqualTo(RiskDecision.REJECT);
        assertThat(rejected.metadata()).containsKeys(
                "acceptedAmount", "projectedAmount", "windowStartUtc", "windowEndUtcExclusive"
        );

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(4)).sumAcceptedOutgoing(eq(1L), from.capture(), to.capture());
        assertThat(from.getAllValues()).allMatch(LocalDateTime.parse("2026-08-13T00:00")::equals);
        assertThat(to.getAllValues()).allMatch(LocalDateTime.parse("2026-08-14T00:00")::equals);
    }

    private RiskContext context(String amount) {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal(amount),
                LocalDateTime.parse("2026-08-13T23:59:59.999")
        );
    }
}
