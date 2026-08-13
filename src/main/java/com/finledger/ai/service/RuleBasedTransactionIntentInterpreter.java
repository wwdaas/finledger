package com.finledger.ai.service;

import com.finledger.ai.exception.UnsupportedAiQueryException;
import com.finledger.ai.model.AnalysisIntent;
import com.finledger.ai.model.AnalysisPeriod;
import com.finledger.ai.model.AnalysisType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "finledger.ai.enabled", havingValue = "false", matchIfMissing = true)
public class RuleBasedTransactionIntentInterpreter implements TransactionIntentInterpreter {

    private static final Pattern LIMIT_PATTERN = Pattern.compile("(\\d{1,2})\\s*笔");
    private static final Pattern CHINESE_LIMIT_PATTERN = Pattern.compile("([一二三四五六七八九十])\\s*笔");
    private static final Pattern THRESHOLD_PATTERN = Pattern.compile(
            "(?:超过|大于|不低于|至少)\\s*(\\d+(?:\\.\\d{1,2})?)"
    );

    @Override
    public AnalysisIntent interpret(String question) {
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        AnalysisPeriod period = resolvePeriod(normalized);

        if (containsAny(normalized, "最大", "top", "最高")) {
            return new AnalysisIntent(
                    AnalysisType.TOP_EXPENSES, period, extractLimit(normalized, 3), new BigDecimal("1000.00")
            );
        }
        if (containsAny(normalized, "大额", "特别大", "异常金额", "large")) {
            return new AnalysisIntent(
                    AnalysisType.LARGE_TRANSACTIONS, AnalysisPeriod.LAST_30_DAYS, 10,
                    extractThreshold(normalized)
            );
        }
        if (containsAny(normalized, "转出去", "转出", "支出", "花了", "outgoing")) {
            return new AnalysisIntent(
                    AnalysisType.MONTHLY_OUTGOING, period, 5, new BigDecimal("1000.00")
            );
        }
        throw new UnsupportedAiQueryException(
                "Only outgoing totals, top expenses, and large transaction queries are supported"
        );
    }

    private AnalysisPeriod resolvePeriod(String question) {
        if (containsAny(question, "上个月", "上月", "last month")) {
            return AnalysisPeriod.PREVIOUS_MONTH;
        }
        if (containsAny(question, "最近", "近30天", "last 30")) {
            return AnalysisPeriod.LAST_30_DAYS;
        }
        return AnalysisPeriod.CURRENT_MONTH;
    }

    private int extractLimit(String question, int defaultLimit) {
        Matcher matcher = LIMIT_PATTERN.matcher(question);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        Matcher chineseMatcher = CHINESE_LIMIT_PATTERN.matcher(question);
        return chineseMatcher.find() ? parseChineseLimit(chineseMatcher.group(1)) : defaultLimit;
    }

    private int parseChineseLimit(String value) {
        return switch (value) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> throw new UnsupportedAiQueryException("Unsupported result limit");
        };
    }

    private BigDecimal extractThreshold(String question) {
        Matcher matcher = THRESHOLD_PATTERN.matcher(question);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : new BigDecimal("1000.00");
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
