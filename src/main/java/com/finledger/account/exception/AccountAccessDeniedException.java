package com.finledger.account.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountAccessDeniedException extends BusinessException {

    public AccountAccessDeniedException(Long accountId) {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED", "Access denied for account: " + accountId);
    }
}
