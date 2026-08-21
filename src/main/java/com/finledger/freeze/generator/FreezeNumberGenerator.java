package com.finledger.freeze.generator;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FreezeNumberGenerator {

    public String nextFreezeNo() {
        return "FRZ" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
