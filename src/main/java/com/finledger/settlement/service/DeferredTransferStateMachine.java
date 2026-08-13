package com.finledger.settlement.service;

import com.finledger.settlement.exception.InvalidTransactionStateException;
import com.finledger.settlement.model.DeferredTransferStatus;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DeferredTransferStateMachine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredTransferStateMachine.class);

    public void requireTransition(String currentStatus, DeferredTransferStatus target) {
        DeferredTransferStatus current;
        try {
            current = DeferredTransferStatus.valueOf(currentStatus);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransactionStateException(currentStatus, target.name());
        }
        if (!current.canTransitionTo(target)) {
            LOGGER.warn("Rejected transaction state transition current={} target={}", current, target);
            throw new InvalidTransactionStateException(currentStatus, target.name());
        }
    }
}
