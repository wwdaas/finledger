package com.finledger.risk.rule;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskLevel;
import com.finledger.risk.model.RiskPhase;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class DailyLimitRiskRule implements RiskRule {

    public static final String CODE = "DAILY_LIMIT";

    private final TransferOrderMapper transferOrderMapper;
    private final RiskProperties properties;

    public DailyLimitRiskRule(
            TransferOrderMapper transferOrderMapper,
            RiskProperties properties
    ) {
        this.transferOrderMapper = transferOrderMapper;
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "Daily outgoing limit";
    }

    @Override
    public RiskPhase phase() {
        return RiskPhase.IN_TRANSACTION;
    }

    @Override
    public RiskEvaluation evaluate(RiskContext context) {
        LocalDateTime from = context.occurredAt().toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        BigDecimal accepted = transferOrderMapper.sumAcceptedOutgoing(context.userId(), from, to);
        BigDecimal projected = accepted.add(context.amount());
        BigDecimal reviewThreshold = properties.getDailyReviewThreshold();
        BigDecimal rejectThreshold = properties.getDailyRejectThreshold();
        Map<String, Object> metadata = Map.of(
                "acceptedAmount", accepted,
                "currentAmount", context.amount(),
                "projectedAmount", projected,
                "reviewThreshold", reviewThreshold,
                "rejectThreshold", rejectThreshold,
                "windowStartUtc", from.toString(),
                "windowEndUtcExclusive", to.toString()
        );
        if (projected.compareTo(reviewThreshold) < 0) {
            return RiskEvaluation.pass(CODE, name(), metadata);
        }
        if (projected.compareTo(rejectThreshold) <= 0) {
            return new RiskEvaluation(
                    CODE, name(), RiskLevel.MEDIUM, RiskDecision.REVIEW,
                    "Projected daily outgoing amount " + projected.toPlainString()
                            + " reached review threshold " + reviewThreshold.toPlainString(),
                    metadata
            );
        }
        return new RiskEvaluation(
                CODE, name(), RiskLevel.HIGH, RiskDecision.REJECT,
                "Projected daily outgoing amount " + projected.toPlainString()
                        + " exceeded reject threshold " + rejectThreshold.toPlainString(),
                metadata
        );
    }
}
