package com.finledger.ai.service;

import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.settlement.dto.DeferredTransferResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "finledger.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DeterministicRiskExplanationGenerator implements TransactionRiskExplanationGenerator {

    @Override
    public String explain(
            String question,
            DeferredTransferResponse transaction,
            List<RiskEventResponse> riskEvents
    ) {
        String base = "交易 %s 当前状态为 %s，风控结论为 %s。".formatted(
                transaction.transferNo(), transaction.status(), transaction.riskDecision()
        );
        if (riskEvents.isEmpty()) {
            return base + " 当前没有触发需要记录的风控规则。";
        }
        String reasons = riskEvents.stream()
                .map(event -> "%s（%s）：%s".formatted(
                        event.ruleCode(), event.riskLevel(), event.reason()
                ))
                .collect(Collectors.joining("；"));
        return base + " 触发记录：" + reasons + "。";
    }
}
