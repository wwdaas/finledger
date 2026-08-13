package com.finledger.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.ai.client.OpenAiResponsesClient;
import com.finledger.ai.model.AnalysisPeriod;
import com.finledger.ai.model.AnalysisType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiTransactionIntentInterpreterTest {

    @Test
    void shouldConvertStructuredModelOutputIntoControlledIntent() {
        OpenAiResponsesClient client = mock(OpenAiResponsesClient.class);
        when(client.generate(anyString(), anyString(), any(), anyString())).thenReturn("""
                {
                  "type": "TOP_EXPENSES",
                  "period": "PREVIOUS_MONTH",
                  "limit": 5,
                  "threshold": 1000.00
                }
                """);
        OpenAiTransactionIntentInterpreter interpreter =
                new OpenAiTransactionIntentInterpreter(client, new ObjectMapper());

        var intent = interpreter.interpret("上个月最大的五笔支出是什么？");

        assertThat(intent.type()).isEqualTo(AnalysisType.TOP_EXPENSES);
        assertThat(intent.period()).isEqualTo(AnalysisPeriod.PREVIOUS_MONTH);
        assertThat(intent.limit()).isEqualTo(5);
        assertThat(intent.threshold()).isEqualByComparingTo("1000.00");
    }
}
