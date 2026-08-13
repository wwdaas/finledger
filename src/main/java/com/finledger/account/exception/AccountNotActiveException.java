package com.finledger.account.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends BusinessException {

    public AccountNotActiveException(Long accountId, String status) {
        super(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE", "Account " + accountId + " is not active: " + status);
    }
}
