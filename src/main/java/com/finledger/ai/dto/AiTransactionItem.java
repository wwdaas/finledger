package com.finledger.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AiTransactionItem(
        String recordNo,
        Long accountId,
        String direction,
        BigDecimal amount,
        String currency,
        Long counterpartyAccountId,
        LocalDateTime createdAt
) {
}
