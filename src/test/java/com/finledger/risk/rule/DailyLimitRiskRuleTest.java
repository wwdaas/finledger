package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyLimitRiskRuleTest {

    @Test
    void shouldRejectProjectedAmountAboveReliableDailyTotal() {
        TransferOrderMapper mapper = mock(TransferOrderMapper.class);
        when(mapper.sumAcceptedOutgoing(eq(1L), any(), any()))
                .thenReturn(new BigDecimal("190000.00"));
        RiskProperties properties = new RiskProperties();
        properties.setDailyLimit(new BigDecimal("200000.00"));
        DailyLimitRiskRule rule = new DailyLimitRiskRule(mapper, properties);

        assertThat(rule.evaluate(context("10000.00")).decision()).isEqualTo(RiskDecision.PASS);
        assertThat(rule.evaluate(context("10000.01")).decision()).isEqualTo(RiskDecision.REJECT);
    }

    private RiskContext context(String amount) {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal(amount),
                LocalDateTime.parse("2026-08-13T12:00:00")
        );
    }
}
