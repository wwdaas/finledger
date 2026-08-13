package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TransactionReferenceExtractor {

    private static final Pattern TRANSFER_NUMBER =
            Pattern.compile("\\bTF[A-F0-9]{24}\\b", Pattern.CASE_INSENSITIVE);

    public String extract(String question) {
        Matcher matcher = TRANSFER_NUMBER.matcher(question);
        if (!matcher.find()) {
            throw new UnsupportedAiQueryException("请在问题中提供以 TF 开头的完整交易号");
        }
        return matcher.group().toUpperCase(Locale.ROOT);
    }
}
