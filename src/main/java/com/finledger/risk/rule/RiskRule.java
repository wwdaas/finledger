package com.finledger.risk.rule;

import com.finledger.risk.model.RiskContext;
import com.finledger.risk.model.RiskEvaluation;
import com.finledger.risk.model.RiskPhase;

public interface RiskRule {

    String code();

    default String name() {
        return code();
    }

    RiskPhase phase();

    RiskEvaluation evaluate(RiskContext context);
}
