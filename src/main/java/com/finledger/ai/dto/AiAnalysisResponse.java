package com.finledger.ai.dto;

import com.finledger.ai.model.AnalysisType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AiAnalysisResponse(
        AnalysisType intent,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String answer,
        BigDecimal totalAmount,
        long transactionCount,
        List<AiTransactionItem> transactions
) {
}
