package com.finledger.ai.service;

import com.finledger.ai.model.AnalysisData;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisWindow;

public interface AnalysisExplanationGenerator {

    String explain(
            String question,
            AnalysisIntent intent,
            AnalysisWindow window,
            AnalysisData data
    );
}
