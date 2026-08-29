package com.finledger.risk.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPropertiesTest {

    @Test
    void shouldRejectReversedReviewAndRejectThresholds() {
        RiskProperties properties = new RiskProperties();
        properties.setHighAmountReviewThreshold(new BigDecimal("50000.01"));
        properties.setFrequencyReviewThreshold(11);
        properties.setDailyReviewThreshold(new BigDecimal("50000.01"));

        assertThat(properties.isHighAmountThresholdOrderValid()).isFalse();
        assertThat(properties.isFrequencyThresholdOrderValid()).isFalse();
        assertThat(properties.isDailyThresholdOrderValid()).isFalse();
    }
}
