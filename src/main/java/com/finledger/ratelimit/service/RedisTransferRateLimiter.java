package com.finledger.ratelimit.service;

import com.finledger.ratelimit.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisTransferRateLimiter implements TransferRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTransferRateLimiter.class);
    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final long maxRequests;
    private final Duration window;

    public RedisTransferRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${finledger.rate-limit.transfer.enabled}") boolean enabled,
            @Value("${finledger.rate-limit.transfer.max-requests}") long maxRequests,
            @Value("${finledger.rate-limit.transfer.window-seconds}") long windowSeconds
    ) {
        if (maxRequests < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Transfer rate-limit settings must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    public void checkAllowed(Long userId) {
        if (!enabled) {
            return;
        }
        String key = "finledger:rate-limit:transfer:user:" + userId;
        try {
            Long count = redisTemplate.execute(
                    FIXED_WINDOW_SCRIPT, List.of(key), Long.toString(window.toMillis())
            );
            if (count != null && count > maxRequests) {
                throw new RateLimitExceededException();
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            LOGGER.warn("Redis rate limiter unavailable; allowing transfer for user {}", userId);
        }
    }
}
