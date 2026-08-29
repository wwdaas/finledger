package com.finledger.ai.service;

import com.finledger.ai.model.TransactionExplanationType;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.settlement.dto.DeferredTransferResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DeterministicRiskExplanationGenerator implements TransactionRiskExplanationGenerator {

    @Override
    public String explain(
            TransactionExplanationType intent,
            String question,
            DeferredTransferResponse transaction,
            List<RiskEventResponse> riskEvents
    ) {
        String base = "交易 %s 当前状态为 %s，风控结论为 %s。".formatted(
                transaction.transferNo(), transaction.status(), transaction.riskDecision()
        );
        if (intent == TransactionExplanationType.QUERY_TRANSACTION_STATUS) {
            return base;
        }
        if (intent == TransactionExplanationType.EXPLAIN_TRANSACTION) {
            return base + "金额为 %s %s；当前总余额、可用余额、冻结余额分别为 %s、%s、%s。"
                    .formatted(
                            transaction.amount(), transaction.currency(), transaction.totalBalance(),
                            transaction.availableBalance(), transaction.frozenBalance()
                    );
        }
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
