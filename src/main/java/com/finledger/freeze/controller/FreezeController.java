package com.finledger.freeze.controller;

import com.finledger.freeze.dto.FreezeRequest;
import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.service.FreezeService;
import com.finledger.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/accounts/{accountId}/freezes")
public class FreezeController {

    private final FreezeService freezeService;

    public FreezeController(FreezeService freezeService) {
        this.freezeService = freezeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FreezeResponse freeze(
            @AuthenticationPrincipal Jwt jwt,
            @Positive @PathVariable Long accountId,
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "Idempotency-Key must use visible ASCII characters")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody FreezeRequest request
    ) {
        return freezeService.freeze(CurrentUser.id(jwt), accountId, idempotencyKey, request);
    }
}
