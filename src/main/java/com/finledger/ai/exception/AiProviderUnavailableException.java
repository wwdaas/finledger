package com.finledger.ai.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AiProviderUnavailableException extends BusinessException {

    public AiProviderUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE", message, cause);
    }
}
