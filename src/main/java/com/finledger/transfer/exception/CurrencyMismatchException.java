package com.finledger.transfer.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException() {
        super(HttpStatus.CONFLICT, "CURRENCY_MISMATCH", "Source and destination account currencies must match");
    }
}
