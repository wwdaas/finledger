package com.finledger.risk.config;

import jakarta.validation.constraints.DecimalMin;
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
    private BigDecimal highAmountThreshold = new BigDecimal("50000.00");

    @Min(1)
    private long frequencyMaxRequests = 5;

    @Min(1)
    private long frequencyWindowSeconds = 60;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal dailyLimit = new BigDecimal("200000.00");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getHighAmountThreshold() { return highAmountThreshold; }
    public void setHighAmountThreshold(BigDecimal highAmountThreshold) {
        this.highAmountThreshold = highAmountThreshold;
    }
    public long getFrequencyMaxRequests() { return frequencyMaxRequests; }
    public void setFrequencyMaxRequests(long frequencyMaxRequests) {
        this.frequencyMaxRequests = frequencyMaxRequests;
    }
    public long getFrequencyWindowSeconds() { return frequencyWindowSeconds; }
    public void setFrequencyWindowSeconds(long frequencyWindowSeconds) {
        this.frequencyWindowSeconds = frequencyWindowSeconds;
    }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
}
