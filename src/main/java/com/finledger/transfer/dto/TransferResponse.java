package com.finledger.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long transferId,
        String transferNo,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        String status,
        BigDecimal fromBalance,
        BigDecimal toBalance,
        LocalDateTime completedAt
) {
}
