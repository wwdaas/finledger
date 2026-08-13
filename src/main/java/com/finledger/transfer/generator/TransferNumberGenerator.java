package com.finledger.transfer.generator;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class TransferNumberGenerator {

    public String nextTransferNo() {
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 24).toUpperCase(Locale.ROOT);
        return "TF" + randomPart;
    }
}
