package com.finledger.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.finledger.ai.exception.AiProviderUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "finledger.ai.enabled", havingValue = "true")
public class OpenAiResponsesClient {

    private final RestClient restClient;
    private final String model;

    public OpenAiResponsesClient(
            RestClient.Builder restClientBuilder,
            @Value("${finledger.ai.api-base-url}") String apiBaseUrl,
            @Value("${finledger.ai.api-key}") String apiKey,
            @Value("${finledger.ai.model}") String model
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI_API_KEY is required when AI_ENABLED=true");
        }
        this.restClient = restClientBuilder
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    public String generate(String instructions, String input, JsonNode jsonSchema, String schemaName) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("instructions", instructions);
        request.put("input", input);
        if (jsonSchema != null) {
            request.put("text", Map.of("format", Map.of(
                    "type", "json_schema",
                    "name", schemaName,
                    "strict", true,
                    "schema", jsonSchema
            )));
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return extractOutputText(response);
        } catch (RestClientException exception) {
            throw new AiProviderUnavailableException("AI provider request failed", exception);
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response != null) {
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())
                            && !content.path("text").asText().isBlank()) {
                        return content.path("text").asText();
                    }
                }
            }
        }
        throw new AiProviderUnavailableException("AI provider returned no text output", null);
    }
}
