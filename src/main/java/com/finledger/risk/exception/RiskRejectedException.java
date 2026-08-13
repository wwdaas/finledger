package com.finledger.risk.exception;

import com.finledger.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RiskRejectedException extends BusinessException {

    private final String transactionNo;

    public RiskRejectedException(String transactionNo) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "RISK_REJECTED",
                "Transaction " + transactionNo + " was rejected by risk control"
        );
        this.transactionNo = transactionNo;
    }

    public String getTransactionNo() {
        return transactionNo;
    }
}
