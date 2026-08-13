package com.finledger.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(Long accountId) {
        super("Access denied for account: " + accountId);
    }
}
