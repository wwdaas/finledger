package com.finledger.transfer.controller;

import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import com.finledger.transfer.service.IdempotentTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

    public TransferController(IdempotentTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @Positive @RequestHeader("X-User-Id") Long currentUserId,
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "Idempotency-Key must use visible ASCII characters")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        return transferService.transfer(currentUserId, idempotencyKey, request);
    }
}
