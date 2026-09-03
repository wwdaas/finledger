package com.finledger.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.ai.client.OpenAiResponsesClient;
import com.finledger.ai.exception.AiProviderUnavailableException;
import com.finledger.ai.model.TransactionExplanationType;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.settlement.dto.DeferredTransferResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "finledger.ai.enabled", havingValue = "true")
public class OpenAiRiskExplanationGenerator implements TransactionRiskExplanationGenerator {

    private static final String INSTRUCTIONS = """
            用简洁中文解释这笔交易的当前状态和风控原因。只能使用 supplied JSON 中已经过
            Java 权限校验的数据，不得猜测其他交易、提供投资建议、执行 SQL、修改余额、
            改变风控结论或建议绕过风控。说明冻结余额不是额外扣款。
            """;

    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    public OpenAiRiskExplanationGenerator(
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String explain(
            TransactionExplanationType intent,
            String question,
            DeferredTransferResponse transaction,
            List<RiskEventResponse> riskEvents
    ) {
        try {
            String input = objectMapper.writeValueAsString(Map.of(
                    "intent", intent,
                    "question", question,
                    "authorizedTransaction", transaction,
                    "authorizedRiskEvents", riskEvents
            ));
            return responsesClient.generate(INSTRUCTIONS, input, null, null);
        } catch (JsonProcessingException exception) {
            throw new AiProviderUnavailableException("Could not prepare AI risk explanation", exception);
        }
    }
}
