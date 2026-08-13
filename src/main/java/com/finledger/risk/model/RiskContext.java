package com.finledger.risk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiskContext(
        Long userId,
        String businessNo,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
}
