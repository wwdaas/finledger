package com.finledger.ledger.generator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class TransactionRecordNumberGenerator {

    public String nextRecordNo() {
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 24).toUpperCase(Locale.ROOT);
        return "TR" + randomPart;
    }
}
