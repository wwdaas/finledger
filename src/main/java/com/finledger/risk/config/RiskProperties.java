package com.finledger.risk.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@Component
@ConfigurationProperties(prefix = "finledger.risk")
public class RiskProperties {

    private boolean enabled = true;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal highAmountReviewThreshold = new BigDecimal("10000.00");

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal highAmountRejectThreshold = new BigDecimal("50000.00");

    @Min(1)
    private long frequencyReviewThreshold = 5;

    @Min(1)
    private long frequencyRejectThreshold = 10;

    @Min(1)
    private long frequencyWindowSeconds = 60;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal dailyReviewThreshold = new BigDecimal("30000.00");

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal dailyRejectThreshold = new BigDecimal("50000.00");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getHighAmountReviewThreshold() { return highAmountReviewThreshold; }
    public void setHighAmountReviewThreshold(BigDecimal highAmountReviewThreshold) {
        this.highAmountReviewThreshold = highAmountReviewThreshold;
    }
    public BigDecimal getHighAmountRejectThreshold() { return highAmountRejectThreshold; }
    public void setHighAmountRejectThreshold(BigDecimal highAmountRejectThreshold) {
        this.highAmountRejectThreshold = highAmountRejectThreshold;
    }
    public long getFrequencyReviewThreshold() { return frequencyReviewThreshold; }
    public void setFrequencyReviewThreshold(long frequencyReviewThreshold) {
        this.frequencyReviewThreshold = frequencyReviewThreshold;
    }
    public long getFrequencyRejectThreshold() { return frequencyRejectThreshold; }
    public void setFrequencyRejectThreshold(long frequencyRejectThreshold) {
        this.frequencyRejectThreshold = frequencyRejectThreshold;
    }
    public long getFrequencyWindowSeconds() { return frequencyWindowSeconds; }
    public void setFrequencyWindowSeconds(long frequencyWindowSeconds) {
        this.frequencyWindowSeconds = frequencyWindowSeconds;
    }
    public BigDecimal getDailyReviewThreshold() { return dailyReviewThreshold; }
    public void setDailyReviewThreshold(BigDecimal dailyReviewThreshold) {
        this.dailyReviewThreshold = dailyReviewThreshold;
    }
    public BigDecimal getDailyRejectThreshold() { return dailyRejectThreshold; }
    public void setDailyRejectThreshold(BigDecimal dailyRejectThreshold) {
        this.dailyRejectThreshold = dailyRejectThreshold;
    }

    @AssertTrue(message = "high amount review threshold must not exceed reject threshold")
    public boolean isHighAmountThresholdOrderValid() {
        return highAmountReviewThreshold == null || highAmountRejectThreshold == null
                || highAmountReviewThreshold.compareTo(highAmountRejectThreshold) <= 0;
    }

    @AssertTrue(message = "frequency review threshold must not exceed reject threshold")
    public boolean isFrequencyThresholdOrderValid() {
        return frequencyReviewThreshold <= frequencyRejectThreshold;
    }

    @AssertTrue(message = "daily review threshold must not exceed reject threshold")
    public boolean isDailyThresholdOrderValid() {
        return dailyReviewThreshold == null || dailyRejectThreshold == null
                || dailyReviewThreshold.compareTo(dailyRejectThreshold) <= 0;
    }
}
