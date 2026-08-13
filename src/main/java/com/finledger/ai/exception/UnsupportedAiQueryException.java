package com.finledger.ai.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UnsupportedAiQueryException extends BusinessException {

    public UnsupportedAiQueryException(String message) {
        super(HttpStatus.BAD_REQUEST, "UNSUPPORTED_AI_QUERY", message);
    }
}
