package com.finledger.idempotency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.idempotency.entity.IdempotencyRecordEntity;
import com.finledger.idempotency.exception.IdempotencyConflictException;
import com.finledger.idempotency.exception.IdempotencyRequestInProgressException;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyReplayService {

    private static final String BUSINESS_TYPE = "TRANSFER";

    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public IdempotencyReplayService(
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    public TransferResponse replay(Long userId, String key, String requestHash) {
        IdempotencyRecordEntity record = idempotencyRecordMapper.selectOne(
                new LambdaQueryWrapper<IdempotencyRecordEntity>()
                        .eq(IdempotencyRecordEntity::getUserId, userId)
                        .eq(IdempotencyRecordEntity::getBusinessType, BUSINESS_TYPE)
                        .eq(IdempotencyRecordEntity::getIdempotencyKey, key)
        );
        if (record == null) {
            throw new IllegalStateException("Duplicate key was raised without an idempotency record");
        }
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        if (!"SUCCESS".equals(record.getStatus()) || record.getResponseSnapshot() == null) {
            throw new IdempotencyRequestInProgressException();
        }
        try {
            return objectMapper.readValue(record.getResponseSnapshot(), TransferResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotency response is invalid", exception);
        }
    }
}
