package com.finledger.ratelimit.service;

public interface TransferRateLimiter {

    void checkAllowed(Long userId);
}
