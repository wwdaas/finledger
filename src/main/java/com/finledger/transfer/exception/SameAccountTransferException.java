package com.finledger.transfer.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SameAccountTransferException extends BusinessException {

    public SameAccountTransferException() {
        super(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT_TRANSFER", "Source and destination accounts must differ");
    }
}
