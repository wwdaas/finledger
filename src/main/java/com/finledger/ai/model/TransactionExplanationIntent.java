package com.finledger.ai.model;

public record TransactionExplanationIntent(
        TransactionExplanationType type,
        String transactionNo
) {
}
