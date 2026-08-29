package com.finledger.ai.service;

import com.finledger.ai.model.TransactionExplanationType;
import com.finledger.risk.dto.RiskEventResponse;
import com.finledger.settlement.dto.DeferredTransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class ResilientRiskExplanationGenerator implements TransactionRiskExplanationGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ResilientRiskExplanationGenerator.class
    );

    private final DeterministicRiskExplanationGenerator fallbackGenerator;
    private final ObjectProvider<OpenAiRiskExplanationGenerator> aiGeneratorProvider;

    public ResilientRiskExplanationGenerator(
            DeterministicRiskExplanationGenerator fallbackGenerator,
            ObjectProvider<OpenAiRiskExplanationGenerator> aiGeneratorProvider
    ) {
        this.fallbackGenerator = fallbackGenerator;
        this.aiGeneratorProvider = aiGeneratorProvider;
    }

    @Override
    public String explain(
            TransactionExplanationType intent,
            String question,
            DeferredTransferResponse transaction,
            List<RiskEventResponse> riskEvents
    ) {
        OpenAiRiskExplanationGenerator aiGenerator = aiGeneratorProvider.getIfAvailable();
        if (aiGenerator != null) {
            try {
                return aiGenerator.explain(intent, question, transaction, riskEvents);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "AI explanation unavailable; using deterministic fallback transactionNo={}",
                        transaction.transferNo()
                );
            }
        }
        return fallbackGenerator.explain(intent, question, transaction, riskEvents);
    }
}
