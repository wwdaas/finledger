package com.finledger.idempotency.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class IdempotencyRequestInProgressException extends BusinessException {

    public IdempotencyRequestInProgressException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "A request with this Idempotency-Key is still being processed");
    }
}
