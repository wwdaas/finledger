package com.finledger.transfer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        @JsonProperty("fromBalance") BigDecimal fromTotalBalance,
        @JsonProperty("toBalance") BigDecimal toTotalBalance,
        LocalDateTime completedAt
) {
}
