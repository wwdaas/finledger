package com.finledger.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserDisabledException extends RuntimeException {

    public UserDisabledException(Long userId) {
        super("User is disabled: " + userId);
    }
}
