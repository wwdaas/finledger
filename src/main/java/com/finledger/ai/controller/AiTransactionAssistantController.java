package com.finledger.ai.controller;

import com.finledger.ai.dto.AiAnalysisResponse;
import com.finledger.ai.dto.AiQueryRequest;
import com.finledger.ai.service.AiTransactionAssistantService;
import com.finledger.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/transactions")
public class AiTransactionAssistantController {

    private final AiTransactionAssistantService assistantService;

    public AiTransactionAssistantController(AiTransactionAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/query")
    public AiAnalysisResponse query(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiQueryRequest request
    ) {
        return assistantService.ask(CurrentUser.id(jwt), request.question());
    }
}
