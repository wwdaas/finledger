package com.finledger.freeze.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidFreezeRequestException extends BusinessException {

    public InvalidFreezeRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_FREEZE_REQUEST", message);
    }
}
