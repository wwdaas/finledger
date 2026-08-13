package com.finledger.risk.service;

import com.finledger.risk.entity.RiskEventEntity;
import com.finledger.risk.mapper.RiskEventMapper;
import com.finledger.risk.model.RiskAssessment;
import com.finledger.risk.model.RiskEvaluation;
import org.springframework.stereotype.Service;

@Service
public class RiskEventRecorder {

    private final RiskEventMapper riskEventMapper;

    public RiskEventRecorder(RiskEventMapper riskEventMapper) {
        this.riskEventMapper = riskEventMapper;
    }

    public void record(
            Long userId,
            Long businessId,
            String businessNo,
            RiskAssessment assessment
    ) {
        for (RiskEvaluation evaluation : assessment.triggeredRules()) {
            RiskEventEntity event = new RiskEventEntity();
            event.setUserId(userId);
            event.setBusinessId(businessId);
            event.setBusinessNo(businessNo);
            event.setRuleCode(evaluation.ruleCode());
            event.setRiskLevel(evaluation.riskLevel().name());
            event.setDecision(evaluation.decision().name());
            event.setReason(evaluation.reason());
            if (riskEventMapper.insert(event) != 1) {
                throw new IllegalStateException("Expected one inserted risk event");
            }
        }
    }
}
