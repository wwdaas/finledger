package com.finledger.ai.service;

import com.finledger.ai.model.AnalysisPeriod;
import com.finledger.ai.model.AnalysisWindow;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Component
public class AnalysisWindowResolver {

    private final Clock clock;

    public AnalysisWindowResolver() {
        this(Clock.systemUTC());
    }

    AnalysisWindowResolver(Clock clock) {
        this.clock = clock;
    }

    public AnalysisWindow resolve(AnalysisPeriod period) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (period == AnalysisPeriod.LAST_30_DAYS) {
            return new AnalysisWindow(now.minusDays(30), now);
        }
        YearMonth month = YearMonth.from(now);
        if (period == AnalysisPeriod.PREVIOUS_MONTH) {
            month = month.minusMonths(1);
        }
        LocalDateTime from = month.atDay(1).atStartOfDay();
        return new AnalysisWindow(from, month.plusMonths(1).atDay(1).atStartOfDay());
    }
}
