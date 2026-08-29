package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import com.finledger.ai.model.TransactionExplanationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedTransactionExplanationIntentInterpreterTest {

    private static final String TRANSACTION_NO = "TF0123456789ABCDEF01234567";
    private final RuleBasedTransactionExplanationIntentInterpreter interpreter =
            new RuleBasedTransactionExplanationIntentInterpreter(new TransactionReferenceExtractor());

    @Test
    void shouldReturnControlledStatusTransactionAndRiskIntents() {
        assertThat(interpreter.interpret("交易 " + TRANSACTION_NO + " 当前状态是什么？").type())
                .isEqualTo(TransactionExplanationType.QUERY_TRANSACTION_STATUS);
        assertThat(interpreter.interpret("请解释交易 " + TRANSACTION_NO).type())
                .isEqualTo(TransactionExplanationType.EXPLAIN_TRANSACTION);
        var risk = interpreter.interpret("交易 " + TRANSACTION_NO + " 为什么触发风控？");
        assertThat(risk.type()).isEqualTo(TransactionExplanationType.EXPLAIN_RISK);
        assertThat(risk.transactionNo()).isEqualTo(TRANSACTION_NO);
    }

    @Test
    void shouldRejectMissingReferenceAndWriteRequests() {
        assertThatThrownBy(() -> interpreter.interpret("这笔交易状态是什么？"))
                .isInstanceOf(UnsupportedAiQueryException.class);
        assertThatThrownBy(() -> interpreter.interpret("帮我转账 100 元"))
                .isInstanceOf(UnsupportedAiQueryException.class);
    }
}
