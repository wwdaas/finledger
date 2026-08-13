package com.finledger.settlement.service;

import com.finledger.settlement.exception.InvalidTransactionStateException;
import com.finledger.settlement.model.DeferredTransferStatus;
import org.springframework.stereotype.Component;

@Component
public class DeferredTransferStateMachine {

    public void requireTransition(String currentStatus, DeferredTransferStatus target) {
        DeferredTransferStatus current;
        try {
            current = DeferredTransferStatus.valueOf(currentStatus);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransactionStateException(currentStatus, target.name());
        }
        if (!current.canTransitionTo(target)) {
            throw new InvalidTransactionStateException(currentStatus, target.name());
        }
    }
}
