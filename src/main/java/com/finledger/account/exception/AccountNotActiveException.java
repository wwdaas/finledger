package com.finledger.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(Long accountId, String status) {
        super("Account " + accountId + " is not active: " + status);
    }
}
