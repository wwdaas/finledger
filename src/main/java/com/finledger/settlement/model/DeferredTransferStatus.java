package com.finledger.settlement.model;

public enum DeferredTransferStatus {
    PENDING,
    SETTLED,
    CANCELLED;

    public boolean canTransitionTo(DeferredTransferStatus target) {
        return this == PENDING && (target == SETTLED || target == CANCELLED);
    }
}
