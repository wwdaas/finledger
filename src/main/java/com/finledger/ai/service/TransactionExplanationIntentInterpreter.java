package com.finledger.ai.service;

import com.finledger.ai.model.TransactionExplanationIntent;

public interface TransactionExplanationIntentInterpreter {

    TransactionExplanationIntent interpret(String question);
}
