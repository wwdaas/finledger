package com.finledger.account.generator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class AccountNumberGenerator {

    public String nextAccountNo() {
        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 24)
                .toUpperCase(Locale.ROOT);
        return "FL" + randomPart;
    }
}
