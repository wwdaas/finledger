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
    public RiskPhase phase() {
        return RiskPhase.IN_TRANSACTION;
    }

    @Override
    public RiskEvaluation evaluate(RiskContext context) {
        LocalDateTime from = context.occurredAt().toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        BigDecimal accepted = transferOrderMapper.sumAcceptedOutgoing(context.userId(), from, to);
        BigDecimal projected = accepted.add(context.amount());
        if (projected.compareTo(properties.getDailyLimit()) <= 0) {
            return RiskEvaluation.pass(CODE);
        }
        return new RiskEvaluation(
                CODE,
                RiskLevel.HIGH,
                RiskDecision.REJECT,
                "Projected daily outgoing amount " + projected.toPlainString()
                        + " exceeds configured limit " + properties.getDailyLimit().toPlainString()
        );
    }
}
