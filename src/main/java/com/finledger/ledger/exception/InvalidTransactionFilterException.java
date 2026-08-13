package com.finledger.ledger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTransactionFilterException extends RuntimeException {

    public InvalidTransactionFilterException(String message) {
        super(message);
    }
}
