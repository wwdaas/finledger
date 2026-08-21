package com.finledger.freeze.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FreezeRequest(
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
        String businessType,

        @Size(max = 255)
        String remark
) {
}
