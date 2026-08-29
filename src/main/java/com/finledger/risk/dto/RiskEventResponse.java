package com.finledger.risk.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record RiskEventResponse(
        Long id,
        Long transactionId,
        String transactionNo,
        String ruleCode,
        String riskLevel,
        String decision,
        BigDecimal amount,
        String reason,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {
}
