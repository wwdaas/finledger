package com.finledger.ai.service;

import com.finledger.ai.model.AnalysisIntent;

public interface TransactionIntentInterpreter {

    AnalysisIntent interpret(String question);
}
