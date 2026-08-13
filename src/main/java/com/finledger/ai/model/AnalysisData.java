package com.finledger.ai.model;

import com.finledger.ai.dto.AiTransactionItem;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisData(
        BigDecimal totalAmount,
        long transactionCount,
        BigDecimal threshold,
        List<AiTransactionItem> transactions
) {
}
