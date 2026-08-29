package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import com.finledger.ai.model.TransactionExplanationIntent;
import com.finledger.ai.model.TransactionExplanationType;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RuleBasedTransactionExplanationIntentInterpreter
        implements TransactionExplanationIntentInterpreter {

    private final TransactionReferenceExtractor referenceExtractor;

    public RuleBasedTransactionExplanationIntentInterpreter(
            TransactionReferenceExtractor referenceExtractor
    ) {
        this.referenceExtractor = referenceExtractor;
    }

    @Override
    public TransactionExplanationIntent interpret(String question) {
        rejectWriteIntent(question);
        String transactionNo = referenceExtractor.extract(question);
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        TransactionExplanationType type;
        if (containsAny(normalized, "风控", "风险", "审核", "拒绝", "拦截", "risk")) {
            type = TransactionExplanationType.EXPLAIN_RISK;
        } else if (containsAny(
                normalized, "状态", "成功了吗", "完成了吗", "到账了吗", "status"
        )) {
            type = TransactionExplanationType.QUERY_TRANSACTION_STATUS;
        } else {
            type = TransactionExplanationType.EXPLAIN_TRANSACTION;
        }
        return new TransactionExplanationIntent(type, transactionNo);
    }

    private void rejectWriteIntent(String question) {
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        if (containsAny(
                normalized, "帮我转", "替我转", "执行转账", "发起转账", "修改余额",
                "取消交易", "清算交易", "直接解冻", "execute transfer", "change balance"
        )) {
            throw new UnsupportedAiQueryException("AI transaction assistant is read-only");
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
