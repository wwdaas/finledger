package com.finledger.idempotency.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with a different request");
    }
}
