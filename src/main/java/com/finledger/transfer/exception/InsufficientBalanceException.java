package com.finledger.transfer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(Long accountId) {
        super("Insufficient balance in account: " + accountId);
    }
}
