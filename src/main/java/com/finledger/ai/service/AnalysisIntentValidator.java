package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisPeriod;
import com.finledger.ai.model.AnalysisType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class AnalysisIntentValidator {

    private static final BigDecimal MAX_THRESHOLD = new BigDecimal("99999999999999999.99");

    public AnalysisIntent validate(AnalysisIntent intent) {
        if (intent == null || intent.type() == null || intent.period() == null) {
            throw new UnsupportedAiQueryException("AI intent is incomplete");
        }
        int limit = Math.max(1, Math.min(intent.limit(), 10));
        BigDecimal threshold = intent.threshold() == null
                ? new BigDecimal("1000.00") : normalizeThreshold(intent.threshold());
        AnalysisPeriod period = intent.type() == AnalysisType.LARGE_TRANSACTIONS
                ? AnalysisPeriod.LAST_30_DAYS : intent.period();
        return new AnalysisIntent(intent.type(), period, limit, threshold);
    }

    private BigDecimal normalizeThreshold(BigDecimal threshold) {
        if (threshold.compareTo(BigDecimal.ZERO) <= 0 || threshold.compareTo(MAX_THRESHOLD) > 0) {
            throw new UnsupportedAiQueryException("Large transaction threshold is outside the allowed range");
        }
        try {
            return threshold.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new UnsupportedAiQueryException("Large transaction threshold supports at most two decimals");
        }
    }
}
