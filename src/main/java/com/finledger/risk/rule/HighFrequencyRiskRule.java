package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskLevel;
import com.finledger.risk.model.RiskPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class HighFrequencyRiskRule implements RiskRule {

    public static final String CODE = "HIGH_FREQUENCY";
    private static final Logger LOGGER = LoggerFactory.getLogger(HighFrequencyRiskRule.class);
    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final RiskProperties properties;

    public HighFrequencyRiskRule(
            StringRedisTemplate redisTemplate,
            RiskProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "High frequency transaction";
    }

    @Override
    public RiskPhase phase() {
        return RiskPhase.PRE_TRANSACTION;
    }

    @Override
    public RiskEvaluation evaluate(RiskContext context) {
        Duration window = Duration.ofSeconds(properties.getFrequencyWindowSeconds());
        String key = "finledger:risk:frequency:user:" + context.userId();
        try {
            Long count = redisTemplate.execute(
                    FIXED_WINDOW_SCRIPT, List.of(key), Long.toString(window.toMillis())
            );
            Map<String, Object> metadata = Map.of(
                    "count", count == null ? 0L : count,
                    "reviewThreshold", properties.getFrequencyReviewThreshold(),
                    "rejectThreshold", properties.getFrequencyRejectThreshold(),
                    "windowSeconds", properties.getFrequencyWindowSeconds()
            );
            if (count != null && count > properties.getFrequencyRejectThreshold()) {
                return new RiskEvaluation(
                        CODE, name(), RiskLevel.HIGH, RiskDecision.REJECT,
                        "Transaction frequency " + count + " exceeded reject threshold "
                                + properties.getFrequencyRejectThreshold(), metadata
                );
            }
            if (count != null && count > properties.getFrequencyReviewThreshold()) {
                return new RiskEvaluation(
                        CODE, name(), RiskLevel.MEDIUM, RiskDecision.REVIEW,
                        "Transaction frequency " + count + " exceeded review threshold "
                                + properties.getFrequencyReviewThreshold(), metadata
                );
            }
            return RiskEvaluation.pass(CODE, name(), metadata);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Redis frequency risk check unavailable; continuing userId={}",
                    context.userId()
            );
        }
        return RiskEvaluation.pass(
                CODE, name(), Map.of("degraded", true, "failurePolicy", "FAIL_OPEN")
        );
    }
}
