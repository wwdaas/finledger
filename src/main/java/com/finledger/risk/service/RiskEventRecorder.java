package com.finledger.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.risk.entity.RiskEventEntity;
import com.finledger.risk.mapper.RiskEventMapper;
import com.finledger.risk.model.RiskAssessment;
import com.finledger.risk.model.RiskEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RiskEventRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiskEventRecorder.class);

    private final RiskEventMapper riskEventMapper;
    private final ObjectMapper objectMapper;

    public RiskEventRecorder(RiskEventMapper riskEventMapper, ObjectMapper objectMapper) {
        this.riskEventMapper = riskEventMapper;
        this.objectMapper = objectMapper;
    }

    public void record(
            Long userId,
            Long businessId,
            String businessNo,
            BigDecimal amount,
            RiskAssessment assessment
    ) {
        for (RiskEvaluation evaluation : assessment.triggeredRules()) {
            RiskEventEntity event = new RiskEventEntity();
            event.setUserId(userId);
            event.setBusinessId(businessId);
            event.setBusinessNo(businessNo);
            event.setAmount(amount);
            event.setRuleCode(evaluation.ruleCode());
            event.setRiskLevel(evaluation.riskLevel().name());
            event.setDecision(evaluation.decision().name());
            event.setReason(evaluation.reason());
            event.setMetadataJson(toJson(evaluation));
            try {
                if (riskEventMapper.insert(event) != 1) {
                    throw new IllegalStateException("Expected one inserted risk event");
                }
            } catch (DuplicateKeyException duplicate) {
                LOGGER.debug(
                        "Risk event already recorded businessNo={} ruleCode={}",
                        businessNo, evaluation.ruleCode()
                );
            }
        }
    }

    private String toJson(RiskEvaluation evaluation) {
        Map<String, Object> metadata = new LinkedHashMap<>(evaluation.metadata());
        metadata.put("ruleName", evaluation.ruleName());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Risk metadata could not be serialized", exception);
        }
    }
}
