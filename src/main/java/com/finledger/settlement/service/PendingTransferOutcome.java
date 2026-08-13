package com.finledger.settlement.service;

import com.finledger.settlement.dto.DeferredTransferResponse;

public record PendingTransferOutcome(
        DeferredTransferResponse response,
        boolean rejected
) {
}
