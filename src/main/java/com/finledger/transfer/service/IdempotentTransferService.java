package com.finledger.transfer.service;

import com.finledger.idempotency.service.IdempotencyReplayService;
import com.finledger.idempotency.service.IdempotencyRequestHasher;
import com.finledger.ratelimit.service.TransferRateLimiter;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class IdempotentTransferService {

    private final IdempotentTransferExecutor transferExecutor;
    private final IdempotencyReplayService replayService;
    private final IdempotencyRequestHasher requestHasher;
    private final TransferRateLimiter rateLimiter;

    public IdempotentTransferService(
            IdempotentTransferExecutor transferExecutor,
            IdempotencyReplayService replayService,
            IdempotencyRequestHasher requestHasher,
            TransferRateLimiter rateLimiter
    ) {
        this.transferExecutor = transferExecutor;
        this.replayService = replayService;
        this.requestHasher = requestHasher;
        this.rateLimiter = rateLimiter;
    }

    public TransferResponse transfer(Long userId, String idempotencyKey, TransferRequest request) {
        rateLimiter.checkAllowed(userId);
        String requestHash = requestHasher.hashTransfer(request);
        try {
            return transferExecutor.execute(userId, idempotencyKey, requestHash, request);
        } catch (DuplicateKeyException exception) {
            return replayService.replay(userId, idempotencyKey, requestHash);
        }
    }
}
