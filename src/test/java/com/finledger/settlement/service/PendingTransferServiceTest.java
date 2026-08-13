package com.finledger.settlement.service;

import com.finledger.risk.exception.RiskRejectedException;
import com.finledger.risk.model.RiskAssessment;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskPhase;
import com.finledger.risk.service.RiskEngine;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.generator.TransferNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingTransferServiceTest {

    @Mock private TransferNumberGenerator transferNumberGenerator;
    @Mock private RiskEngine riskEngine;
    @Mock private PendingTransferExecutor executor;

    private PendingTransferService pendingTransferService;

    @BeforeEach
    void setUp() {
        pendingTransferService = new PendingTransferService(
                transferNumberGenerator, riskEngine, executor
        );
    }

    @Test
    void shouldRunPreTransactionRiskBeforeDelegatingToTransactionalExecutor() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("300.00"));
        DeferredTransferResponse response = response("PENDING", "PASS");
        when(transferNumberGenerator.nextTransferNo()).thenReturn("TF-PENDING-1");
        when(riskEngine.evaluate(any(RiskContext.class), eq(RiskPhase.PRE_TRANSACTION)))
                .thenReturn(RiskAssessment.pass());
        when(executor.execute(
                eq(10L), eq(request), eq(new BigDecimal("300.00")), eq("TF-PENDING-1"),
                any(LocalDateTime.class), eq(RiskAssessment.pass())
        )).thenReturn(new PendingTransferOutcome(response, false));

        var result = pendingTransferService.createPending(10L, request);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.availableBalance()).isEqualByComparingTo("700.00");
        assertThat(result.frozenBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void shouldThrowOnlyAfterRejectedOutcomeHasReturnedFromTransaction() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("300.00"));
        when(transferNumberGenerator.nextTransferNo()).thenReturn("TF-REJECTED-1");
        when(riskEngine.evaluate(any(RiskContext.class), eq(RiskPhase.PRE_TRANSACTION)))
                .thenReturn(RiskAssessment.pass());
        when(executor.execute(
                eq(10L), eq(request), eq(new BigDecimal("300.00")), eq("TF-REJECTED-1"),
                any(LocalDateTime.class), eq(RiskAssessment.pass())
        )).thenReturn(new PendingTransferOutcome(response("FAILED", "REJECT"), true));

        assertThatThrownBy(() -> pendingTransferService.createPending(10L, request))
                .isInstanceOf(RiskRejectedException.class)
                .extracting("transactionNo")
                .isEqualTo("TF-REJECTED-1");
    }

    private DeferredTransferResponse response(String status, String riskDecision) {
        return new DeferredTransferResponse(
                99L, "TF-PENDING-1", 1L, 2L, new BigDecimal("300.00"), "CNY",
                status, riskDecision, new BigDecimal("1000.00"),
                new BigDecimal("700.00"), new BigDecimal("300.00"),
                LocalDateTime.now(), null
        );
    }
}
