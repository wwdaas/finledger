package com.finledger.transfer.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(Long accountId) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "Insufficient balance in account: " + accountId);
    }
}
