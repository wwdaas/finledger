package com.finledger.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "fromAccountId is required")
        @Positive(message = "fromAccountId must be positive")
        Long fromAccountId,

        @NotNull(message = "toAccountId is required")
        @Positive(message = "toAccountId must be positive")
        Long toAccountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "amount must fit DECIMAL(19,2)")
        BigDecimal amount
) {
}
