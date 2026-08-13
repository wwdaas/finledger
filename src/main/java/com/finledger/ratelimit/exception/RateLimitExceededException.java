package com.finledger.ratelimit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Transfer rate limit exceeded; retry later");
    }
}
