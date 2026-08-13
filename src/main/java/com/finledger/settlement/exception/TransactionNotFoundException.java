package com.finledger.settlement.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends BusinessException {

    public TransactionNotFoundException(Long transactionId) {
        super(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found: " + transactionId);
    }

    public TransactionNotFoundException(String transactionNo) {
        super(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found: " + transactionNo);
    }
}
