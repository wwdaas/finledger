package com.finledger.account.controller;

import com.finledger.account.dto.AccountResponse;
import com.finledger.account.service.AccountService;
import com.finledger.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.create(CurrentUser.id(jwt));
    }

    @GetMapping
    public List<AccountResponse> list(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountService.listByUser(CurrentUser.id(jwt));
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(
            @AuthenticationPrincipal Jwt jwt,
            @jakarta.validation.constraints.Positive @PathVariable Long accountId
    ) {
        return accountService.getOwnedAccount(CurrentUser.id(jwt), accountId);
    }
}
