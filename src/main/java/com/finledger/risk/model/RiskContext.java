package com.finledger.risk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiskContext(
        Long userId,
        String businessNo,
        String transactionType,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
    public RiskContext(
            Long userId,
            String businessNo,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        this(
                userId, businessNo, "DEFERRED_TRANSFER", fromAccountId, toAccountId,
                amount, occurredAt
        );
    }
}
