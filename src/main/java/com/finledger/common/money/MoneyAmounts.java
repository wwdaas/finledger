package com.finledger.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyAmounts {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

    private MoneyAmounts() {
    }

    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidAmountException("Amount exceeds DECIMAL(19,2) range");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidAmountException("Amount must have at most two decimal places");
        }
    }

    public static BigDecimal requireValidBalance(BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) < 0 || balance.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidAmountException("Resulting balance is outside DECIMAL(19,2) range");
        }
        return balance.setScale(2, RoundingMode.UNNECESSARY);
    }
}
