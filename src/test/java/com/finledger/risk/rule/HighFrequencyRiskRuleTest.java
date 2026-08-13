package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighFrequencyRiskRuleTest {

    @Test
    void shouldReviewRequestsAboveConfiguredFrequency() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(6L);
        HighFrequencyRiskRule rule = new HighFrequencyRiskRule(redisTemplate, properties());

        assertThat(rule.evaluate(context()).decision()).isEqualTo(RiskDecision.REVIEW);
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
        properties.setFrequencyMaxRequests(5);
        properties.setFrequencyWindowSeconds(60);
        return properties;
    }

    private RiskContext context() {
        return new RiskContext(
                1L, "TF1", 10L, 20L, new BigDecimal("10.00"), LocalDateTime.now()
        );
    }
}
