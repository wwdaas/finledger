package com.finledger.transfer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException() {
        super("Source and destination account currencies must match");
    }
}
