package com.finledger.recharge.dto;

import java.math.BigDecimal;

public record RechargeResponse(
        Long businessId,
        String recordNo,
        Long accountId,
        BigDecimal amount,
        BigDecimal balance
) {
}
