package com.finledger.transfer.controller;

import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.settlement.service.PendingTransferService;
import com.finledger.transfer.service.IdempotentTransferService;
import com.finledger.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final IdempotentTransferService transferService;
    private final PendingTransferService pendingTransferService;

    public TransferController(
            IdempotentTransferService transferService,
            PendingTransferService pendingTransferService
    ) {
        this.transferService = transferService;
        this.pendingTransferService = pendingTransferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @AuthenticationPrincipal Jwt jwt,
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "Idempotency-Key must use visible ASCII characters")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        return transferService.transfer(CurrentUser.id(jwt), idempotencyKey, request);
    }

    @PostMapping("/pending")
    @ResponseStatus(HttpStatus.CREATED)
    public DeferredTransferResponse createPending(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferRequest request
    ) {
        return pendingTransferService.createPending(CurrentUser.id(jwt), request);
    }
}
