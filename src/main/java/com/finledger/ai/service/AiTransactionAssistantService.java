package com.finledger.ai.service;

import com.finledger.ai.dto.AiAnalysisResponse;
import com.finledger.ai.model.AnalysisData;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisWindow;
import org.springframework.stereotype.Service;

@Service
public class AiTransactionAssistantService {

    private final TransactionIntentInterpreter intentInterpreter;
    private final AnalysisIntentValidator intentValidator;
    private final AnalysisWindowResolver windowResolver;
    private final TransactionAnalyticsService analyticsService;
    private final AnalysisExplanationGenerator explanationGenerator;

    public AiTransactionAssistantService(
            TransactionIntentInterpreter intentInterpreter,
            AnalysisIntentValidator intentValidator,
            AnalysisWindowResolver windowResolver,
            TransactionAnalyticsService analyticsService,
            AnalysisExplanationGenerator explanationGenerator
    ) {
        this.intentInterpreter = intentInterpreter;
        this.intentValidator = intentValidator;
        this.windowResolver = windowResolver;
        this.analyticsService = analyticsService;
        this.explanationGenerator = explanationGenerator;
    }

    public AiAnalysisResponse ask(Long userId, String question) {
        AnalysisIntent intent = intentValidator.validate(intentInterpreter.interpret(question));
        AnalysisWindow window = windowResolver.resolve(intent.period());
        AnalysisData data = analyticsService.analyze(userId, intent, window);
        String answer = explanationGenerator.explain(question, intent, window, data);
        return new AiAnalysisResponse(
                intent.type(), window.from(), window.to(), answer, data.totalAmount(),
                data.transactionCount(), data.transactions()
        );
    }
}
