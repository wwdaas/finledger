package com.finledger.ai.service;

import com.finledger.ai.dto.AiAnalysisResponse;
import com.finledger.ai.model.AnalysisData;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisPeriod;
import com.finledger.ai.model.AnalysisType;
import com.finledger.ai.model.AnalysisWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTransactionAssistantServiceTest {

    @Mock private TransactionIntentInterpreter interpreter;
    @Mock private AnalysisIntentValidator validator;
    @Mock private AnalysisWindowResolver windowResolver;
    @Mock private TransactionAnalyticsService analyticsService;
    @Mock private AnalysisExplanationGenerator explanationGenerator;

    @Test
    void shouldKeepIdentityAndDataAccessInsideJavaPipeline() {
        AnalysisIntent intent = new AnalysisIntent(
                AnalysisType.MONTHLY_OUTGOING, AnalysisPeriod.CURRENT_MONTH, 5,
                new BigDecimal("1000.00")
        );
        AnalysisWindow window = new AnalysisWindow(
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-09-01T00:00:00")
        );
        AnalysisData data = new AnalysisData(new BigDecimal("88.00"), 2, intent.threshold(), List.of());
        when(interpreter.interpret("question")).thenReturn(intent);
        when(validator.validate(intent)).thenReturn(intent);
        when(windowResolver.resolve(intent.period())).thenReturn(window);
        when(analyticsService.analyze(42L, intent, window)).thenReturn(data);
        when(explanationGenerator.explain("question", intent, window, data)).thenReturn("answer");
        AiTransactionAssistantService service = new AiTransactionAssistantService(
                interpreter, validator, windowResolver, analyticsService, explanationGenerator
        );

        AiAnalysisResponse response = service.ask(42L, "question");

        assertThat(response.totalAmount()).isEqualByComparingTo("88.00");
        assertThat(response.answer()).isEqualTo("answer");
        verify(analyticsService).analyze(42L, intent, window);
    }
}
