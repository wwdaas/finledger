package com.finledger.settlement.service;

import com.finledger.settlement.exception.InvalidTransactionStateException;
import com.finledger.settlement.model.DeferredTransferStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeferredTransferStateMachineTest {

    private final DeferredTransferStateMachine stateMachine = new DeferredTransferStateMachine();

    @Test
    void shouldAllowOnlyPendingToFinalTransitions() {
        assertThatCode(() -> stateMachine.requireTransition("PENDING", DeferredTransferStatus.SETTLED))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.requireTransition("PENDING", DeferredTransferStatus.CANCELLED))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> stateMachine.requireTransition("SETTLED", DeferredTransferStatus.CANCELLED))
                .isInstanceOf(InvalidTransactionStateException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition("CANCELLED", DeferredTransferStatus.SETTLED))
                .isInstanceOf(InvalidTransactionStateException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition("SETTLED", DeferredTransferStatus.SETTLED))
                .isInstanceOf(InvalidTransactionStateException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition("CANCELLED", DeferredTransferStatus.CANCELLED))
                .isInstanceOf(InvalidTransactionStateException.class);
    }
}
