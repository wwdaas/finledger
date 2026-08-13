package com.finledger.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String accountNo,
        BigDecimal balance,
        String currency,
        String status,
        Long version,
        LocalDateTime createdAt
) {
}
