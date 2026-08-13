package com.finledger.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.ai.client.OpenAiResponsesClient;
import com.finledger.ai.exception.AiProviderUnavailableException;
import com.finledger.ai.model.AnalysisData;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisWindow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "finledger.ai.enabled", havingValue = "true")
public class OpenAiExplanationGenerator implements AnalysisExplanationGenerator {

    private static final String INSTRUCTIONS = """
            Explain only the supplied, already-authorized transaction analysis in concise Chinese.
            Do not invent records, give financial advice, request more data, execute SQL, or suggest
            changing a balance. Numeric values and dates must come only from the supplied JSON.
            """;

    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    public OpenAiExplanationGenerator(
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String explain(
            String question,
            AnalysisIntent intent,
            AnalysisWindow window,
            AnalysisData data
    ) {
        try {
            String input = objectMapper.writeValueAsString(Map.of(
                    "question", question,
                    "intent", intent,
                    "period", window,
                    "authorizedData", data
            ));
            return responsesClient.generate(INSTRUCTIONS, input, null, null);
        } catch (JsonProcessingException exception) {
            throw new AiProviderUnavailableException("Could not prepare AI explanation", exception);
        }
    }
}
