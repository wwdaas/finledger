package com.finledger.account.controller;

import com.finledger.account.dto.AccountResponse;
import com.finledger.account.service.AccountService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @Positive @RequestHeader(USER_ID_HEADER) Long currentUserId
    ) {
        return accountService.create(currentUserId);
    }

    @GetMapping
    public List<AccountResponse> list(
            @Positive @RequestHeader(USER_ID_HEADER) Long currentUserId
    ) {
        return accountService.listByUser(currentUserId);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(
            @Positive @RequestHeader(USER_ID_HEADER) Long currentUserId,
            @Positive @PathVariable Long accountId
    ) {
        return accountService.getOwnedAccount(currentUserId, accountId);
    }
}
