package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionReferenceExtractorTest {

    private final TransactionReferenceExtractor extractor = new TransactionReferenceExtractor();

    @Test
    void shouldExtractAndNormalizeTransferNumber() {
        String result = extractor.extract(
                "请解释交易 tf0123456789abcdef01234567 为什么被风控"
        );

        assertThat(result).isEqualTo("TF0123456789ABCDEF01234567");
    }

    @Test
    void shouldRejectQuestionWithoutCompleteTransferNumber() {
        assertThatThrownBy(() -> extractor.extract("为什么这笔交易失败？"))
                .isInstanceOf(UnsupportedAiQueryException.class);
    }
}
