package com.finledger.freeze.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FreezeResponse(
        Long freezeId,
        String freezeNo,
        Long accountId,
        BigDecimal amount,
        String businessType,
        String remark,
        String status,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        BigDecimal totalBalance,
        LocalDateTime createdAt
) {
}
