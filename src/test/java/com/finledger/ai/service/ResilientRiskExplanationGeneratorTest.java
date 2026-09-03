package com.finledger.ai.service;

import com.finledger.ai.exception.AiProviderUnavailableException;
import com.finledger.ai.model.TransactionExplanationType;
import com.finledger.settlement.dto.DeferredTransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientRiskExplanationGeneratorTest {

    @Test
    void shouldFallBackToDeterministicAnswerWhenAiProviderFails() {
        OpenAiRiskExplanationGenerator aiGenerator = mock(OpenAiRiskExplanationGenerator.class);
        when(aiGenerator.explain(any(), any(), any(), any()))
                .thenThrow(new AiProviderUnavailableException("provider failed", null));
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenAiRiskExplanationGenerator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiGenerator);
        ResilientRiskExplanationGenerator generator = new ResilientRiskExplanationGenerator(
                new DeterministicRiskExplanationGenerator(), provider
        );
        DeferredTransferResponse transaction = new DeferredTransferResponse(
                7L, "TF0123456789ABCDEF01234567", 10L, 11L,
                new BigDecimal("300.00"), "CNY", "PENDING", "PASS",
                new BigDecimal("1000.00"), new BigDecimal("700.00"),
                new BigDecimal("300.00"), LocalDateTime.parse("2026-08-13T12:00:00"), null
        );

        String answer = generator.explain(
                TransactionExplanationType.QUERY_TRANSACTION_STATUS,
                "状态是什么", transaction, List.of()
        );

        assertThat(answer).contains(transaction.transferNo(), "PENDING", "PASS");
    }
}
