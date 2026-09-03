package com.finledger.ai.service;

import com.finledger.ai.model.TransactionExplanationIntent;
import com.finledger.ai.model.TransactionExplanationType;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.risk.service.RiskEventQueryService;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.settlement.service.DeferredTransferQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRiskExplanationServiceTest {

    private static final String TRANSACTION_NO = "TF0123456789ABCDEF01234567";

    @Mock private TransactionExplanationIntentInterpreter intentInterpreter;
    @Mock private DeferredTransferQueryService transferQueryService;
    @Mock private RiskEventQueryService riskEventQueryService;
    @Mock private TransactionRiskExplanationGenerator explanationGenerator;

    @Test
    void shouldAuthorizeTransactionAndRiskQueriesWithSameUserId() {
        DeferredTransferResponse transaction = new DeferredTransferResponse(
                7L, TRANSACTION_NO, 10L, 11L, new BigDecimal("300.00"), "CNY",
                "PENDING", "REVIEW", new BigDecimal("1000.00"),
                new BigDecimal("700.00"), new BigDecimal("300.00"),
                LocalDateTime.parse("2026-08-13T12:00:00"), null
        );
        RiskEventResponse event = new RiskEventResponse(
                9L, 7L, TRANSACTION_NO, "HIGH_AMOUNT", "MEDIUM", "REVIEW",
                new BigDecimal("300.00"), "amount exceeds threshold",
                Map.of("ruleName", "High amount transaction"),
                LocalDateTime.parse("2026-08-13T12:00:00")
        );
        when(intentInterpreter.interpret("question")).thenReturn(
                new TransactionExplanationIntent(
                        TransactionExplanationType.EXPLAIN_RISK, TRANSACTION_NO
                )
        );
        when(transferQueryService.getOwnedByNo(42L, TRANSACTION_NO)).thenReturn(transaction);
        when(riskEventQueryService.findByBusinessNo(42L, TRANSACTION_NO)).thenReturn(List.of(event));
        when(explanationGenerator.explain(
                TransactionExplanationType.EXPLAIN_RISK, "question", transaction, List.of(event)
        ))
                .thenReturn("answer");
        AiRiskExplanationService service = new AiRiskExplanationService(
                intentInterpreter, transferQueryService, riskEventQueryService, explanationGenerator
        );

        var response = service.explain(42L, "question");

        assertThat(response.answer()).isEqualTo("answer");
        assertThat(response.intent()).isEqualTo(TransactionExplanationType.EXPLAIN_RISK);
        assertThat(response.availableBalance()).isEqualByComparingTo("700.00");
        verify(transferQueryService).getOwnedByNo(42L, TRANSACTION_NO);
        verify(riskEventQueryService).findByBusinessNo(42L, TRANSACTION_NO);
    }
}
