package com.finledger.account.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InsufficientAvailableBalanceException extends BusinessException {

    public InsufficientAvailableBalanceException(Long accountId) {
        super(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_AVAILABLE_BALANCE",
                "Insufficient available balance in account: " + accountId
        );
    }
}
