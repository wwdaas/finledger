package com.finledger.ledger.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidTransactionFilterException extends BusinessException {

    public InvalidTransactionFilterException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION_FILTER", message);
    }
}
