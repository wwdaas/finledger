package com.finledger.risk.dto;

import java.time.LocalDateTime;

public record RiskEventResponse(
        Long id,
        String businessNo,
        String ruleCode,
        String riskLevel,
        String decision,
        String reason,
        LocalDateTime createdAt
) {
}
