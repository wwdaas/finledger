package com.finledger.ratelimit.service;

import com.finledger.ratelimit.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisTransferRateLimiterTest {

    @Test
    void shouldRejectRequestOverLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(3L);
        RedisTransferRateLimiter limiter = new RedisTransferRateLimiter(redisTemplate, true, 2, 60);

        assertThatThrownBy(() -> limiter.checkAllowed(7L))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldFailOpenWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("timeout"));
        RedisTransferRateLimiter limiter = new RedisTransferRateLimiter(redisTemplate, true, 2, 60);

        assertThatCode(() -> limiter.checkAllowed(7L)).doesNotThrowAnyException();
    }
}
