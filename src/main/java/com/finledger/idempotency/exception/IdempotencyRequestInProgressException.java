package com.finledger.idempotency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException() {
        super("A request with this Idempotency-Key is still being processed");
    }
}
