package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighFrequencyRiskRuleTest {

    @Test
    void shouldApplyUserScopedPassReviewAndRejectBoundaries() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(5L, 6L, 10L, 11L);
        HighFrequencyRiskRule rule = new HighFrequencyRiskRule(redisTemplate, properties());

        assertThat(rule.evaluate(context()).decision()).isEqualTo(RiskDecision.PASS);
        assertThat(rule.evaluate(context()).decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(rule.evaluate(context()).decision()).isEqualTo(RiskDecision.REVIEW);
        var rejected = rule.evaluate(context());
        assertThat(rejected.decision()).isEqualTo(RiskDecision.REJECT);
        assertThat(rejected.metadata()).containsEntry("count", 11L)
                .containsEntry("windowSeconds", 60L);
        verify(redisTemplate, times(4)).execute(
                any(), org.mockito.ArgumentMatchers.eq(List.of("finledger:risk:frequency:user:1")),
                org.mockito.ArgumentMatchers.eq("60000")
        );
    }

    @Test
    void shouldFailOpenWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("timeout"));
        HighFrequencyRiskRule rule = new HighFrequencyRiskRule(redisTemplate, properties());

        assertThat(rule.evaluate(context()).decision()).isEqualTo(RiskDecision.PASS);
    }

    private RiskProperties properties() {
        RiskProperties properties = new RiskProperties();
        properties.setFrequencyReviewThreshold(5);
        properties.setFrequencyRejectThreshold(10);
        properties.setFrequencyWindowSeconds(60);
        return properties;
    }

    private RiskContext context() {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal("10.00"), LocalDateTime.now()
        );
    }
}
