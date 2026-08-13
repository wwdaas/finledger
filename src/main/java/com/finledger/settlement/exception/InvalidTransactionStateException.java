package com.finledger.settlement.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidTransactionStateException extends BusinessException {

    public InvalidTransactionStateException(String currentStatus, String requestedStatus) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_TRANSACTION_STATE",
                "Cannot transition transaction from " + currentStatus + " to " + requestedStatus
        );
    }
}
