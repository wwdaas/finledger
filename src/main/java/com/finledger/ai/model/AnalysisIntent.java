package com.finledger.ai.model;

import java.math.BigDecimal;

public record AnalysisIntent(
        AnalysisType type,
        AnalysisPeriod period,
        int limit,
        BigDecimal threshold
) {
}
