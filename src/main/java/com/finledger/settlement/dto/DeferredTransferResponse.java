package com.finledger.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeferredTransferResponse(
        Long transferId,
        String transferNo,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        String status,
        String riskDecision,
        BigDecimal totalBalance,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
