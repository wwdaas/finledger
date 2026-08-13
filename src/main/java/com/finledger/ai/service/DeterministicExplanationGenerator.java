package com.finledger.ai.service;

import com.finledger.ai.model.AnalysisData;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisType;
import com.finledger.ai.model.AnalysisWindow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "finledger.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DeterministicExplanationGenerator implements AnalysisExplanationGenerator {

    @Override
    public String explain(
            String question,
            AnalysisIntent intent,
            AnalysisWindow window,
            AnalysisData data
    ) {
        if (intent.type() == AnalysisType.MONTHLY_OUTGOING) {
            return String.format(
                    "%s 至 %s 共转出 %s 元，共 %d 笔。",
                    window.from().toLocalDate(), window.to().toLocalDate(),
                    data.totalAmount().toPlainString(), data.transactionCount()
            );
        }
        if (intent.type() == AnalysisType.TOP_EXPENSES) {
            return String.format(
                    "%s 至 %s 共找到 %d 笔转出，以下列出金额最大的 %d 笔。",
                    window.from().toLocalDate(), window.to().toLocalDate(),
                    data.transactionCount(), data.transactions().size()
            );
        }
        return String.format(
                "最近 30 天找到 %d 笔金额不低于 %s 元的交易。",
                data.transactionCount(), data.threshold().toPlainString()
        );
    }
}
