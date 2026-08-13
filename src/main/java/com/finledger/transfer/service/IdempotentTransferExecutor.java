package com.finledger.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.idempotency.entity.IdempotencyRecordEntity;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentTransferExecutor {

    private static final String BUSINESS_TYPE = "TRANSFER";

    private final TransferService transferService;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public IdempotentTransferExecutor(
            TransferService transferService,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.transferService = transferService;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransferResponse execute(
            Long userId,
            String idempotencyKey,
            String requestHash,
            TransferRequest request
    ) {
        IdempotencyRecordEntity claim = new IdempotencyRecordEntity();
        claim.setUserId(userId);
        claim.setBusinessType(BUSINESS_TYPE);
        claim.setIdempotencyKey(idempotencyKey);
        claim.setRequestHash(requestHash);
        claim.setStatus("PROCESSING");
        if (idempotencyRecordMapper.insert(claim) != 1) {
            throw new IllegalStateException("Expected one inserted idempotency claim");
        }

        TransferResponse response = transferService.transfer(userId, request);
        claim.setStatus("SUCCESS");
        claim.setResourceId(response.transferId());
        claim.setResponseSnapshot(writeSnapshot(response));
        if (idempotencyRecordMapper.updateById(claim) != 1) {
            throw new IllegalStateException("Expected one updated idempotency claim");
        }
        return response;
    }

    private String writeSnapshot(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store idempotency response", exception);
        }
    }
}
