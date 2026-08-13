package com.finledger.user.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends BusinessException {

    public UsernameAlreadyExistsException(String username, Throwable cause) {
        super(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "Username already exists: " + username, cause);
    }
}
