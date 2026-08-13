package com.finledger.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRecordResponse(
        Long id,
        String recordNo,
        Long accountId,
        String businessType,
        Long businessId,
        String direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        Long counterpartyAccountId,
        LocalDateTime createdAt
) {
}
