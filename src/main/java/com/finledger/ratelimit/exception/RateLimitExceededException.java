package com.finledger.ratelimit.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Transfer rate limit exceeded; retry later");
    }
}
