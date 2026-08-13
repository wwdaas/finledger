package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import com.finledger.ai.model.AnalysisPeriod;
import com.finledger.ai.model.AnalysisType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedTransactionIntentInterpreterTest {

    private final RuleBasedTransactionIntentInterpreter interpreter =
            new RuleBasedTransactionIntentInterpreter();

    @Test
    void shouldUnderstandMonthlyOutgoingQuestion() {
        var intent = interpreter.interpret("我这个月转出去多少钱？");

        assertThat(intent.type()).isEqualTo(AnalysisType.MONTHLY_OUTGOING);
        assertThat(intent.period()).isEqualTo(AnalysisPeriod.CURRENT_MONTH);
    }

    @Test
    void shouldUnderstandTopExpensesQuestion() {
        var intent = interpreter.interpret("上个月最大的五笔支出是什么？");

        assertThat(intent.type()).isEqualTo(AnalysisType.TOP_EXPENSES);
        assertThat(intent.period()).isEqualTo(AnalysisPeriod.PREVIOUS_MONTH);
        assertThat(intent.limit()).isEqualTo(5);
    }

    @Test
    void shouldRejectAnyWriteRequest() {
        assertThatThrownBy(() -> interpreter.interpret("帮我给账户2转账100元"))
                .isInstanceOf(UnsupportedAiQueryException.class);
    }
}
