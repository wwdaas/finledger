package com.finledger.risk.service;

import com.finledger.risk.config.RiskProperties;
import com.finledger.risk.model.RiskAssessment;
import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskDecision;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskPhase;
import com.finledger.risk.rule.RiskRule;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskEngine {

    private final List<RiskRule> rules;
    private final RiskProperties properties;

    public RiskEngine(List<RiskRule> rules, RiskProperties properties) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
    }

    public RiskAssessment evaluate(RiskContext context, RiskPhase phase) {
        if (!properties.isEnabled()) {
            return RiskAssessment.pass();
        }
        List<RiskEvaluation> triggered = rules.stream()
                .filter(rule -> rule.phase() == phase)
                .map(rule -> rule.evaluate(context))
                .filter(RiskEvaluation::triggered)
                .toList();
        RiskDecision decision = triggered.stream()
                .map(RiskEvaluation::decision)
                .reduce(RiskDecision.PASS, RiskDecision::mostRestrictive);
        return new RiskAssessment(decision, triggered);
    }
}
