package com.finledger.user.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserDisabledException extends BusinessException {

    public UserDisabledException(Long userId) {
        super(HttpStatus.FORBIDDEN, "USER_DISABLED", "User is disabled: " + userId);
    }
}
