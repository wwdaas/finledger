package com.finledger.idempotency.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends BusinessException {

    public IdempotencyConflictException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key was already used with a different request");
    }
}
