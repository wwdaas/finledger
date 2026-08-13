package com.finledger.settlement.generator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class FundMovementNumberGenerator {

    public String nextMovementNo() {
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 24).toUpperCase(Locale.ROOT);
        return "FM" + randomPart;
    }
}
