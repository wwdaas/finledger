package com.finledger.risk.controller;

import com.finledger.common.api.PageResponse;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.risk.service.RiskEventQueryService;
import com.finledger.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/risk-events")
public class RiskEventController {

    private final RiskEventQueryService queryService;

    public RiskEventController(RiskEventQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PageResponse<RiskEventResponse> query(
            @AuthenticationPrincipal Jwt jwt,
            @Size(max = 40) @RequestParam(required = false) String businessNo,
            @Min(1) @RequestParam(defaultValue = "1") long page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") long size
    ) {
        return queryService.query(CurrentUser.id(jwt), businessNo, page, size);
    }
}
