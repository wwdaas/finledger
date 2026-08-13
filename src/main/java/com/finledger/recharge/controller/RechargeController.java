package com.finledger.recharge.controller;

import com.finledger.recharge.dto.RechargeRequest;
import com.finledger.recharge.dto.RechargeResponse;
import com.finledger.recharge.service.RechargeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/accounts/{accountId}/recharges")
public class RechargeController {

    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RechargeResponse recharge(
            @Positive @RequestHeader("X-User-Id") Long currentUserId,
            @Positive @PathVariable Long accountId,
            @Valid @RequestBody RechargeRequest request
    ) {
        return rechargeService.recharge(currentUserId, accountId, request.amount());
    }
}
