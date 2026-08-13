package com.finledger.ledger.controller;

import com.finledger.common.api.PageResponse;
import com.finledger.ledger.dto.TransactionRecordResponse;
import com.finledger.ledger.service.TransactionRecordService;
import com.finledger.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/transactions")
public class TransactionRecordController {

    private final TransactionRecordService transactionRecordService;

    public TransactionRecordController(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
    }

    @GetMapping
    public PageResponse<TransactionRecordResponse> query(
            @AuthenticationPrincipal Jwt jwt,
            @Positive @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String direction,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to,
            @Min(1) @RequestParam(defaultValue = "1") long page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") long size
    ) {
        return transactionRecordService.query(
                CurrentUser.id(jwt), accountId, businessType, direction, from, to, page, size
        );
    }
}
