package com.finledger.freeze.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.common.money.MoneyAmounts;
import com.finledger.freeze.dto.FreezeRequest;
import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.exception.InvalidFreezeRequestException;
import com.finledger.idempotency.entity.IdempotencyRecordEntity;
import com.finledger.idempotency.exception.IdempotencyConflictException;
import com.finledger.idempotency.exception.IdempotencyRequestInProgressException;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.idempotency.service.IdempotencyRequestHasher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Service
public class FreezeService {

    private static final Pattern BUSINESS_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[\\x21-\\x7E]+$");

    private final FreezeExecutor executor;
    private final IdempotencyRequestHasher requestHasher;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    public FreezeService(
            FreezeExecutor executor,
            IdempotencyRequestHasher requestHasher,
            IdempotencyRecordMapper idempotencyRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.executor = executor;
        this.requestHasher = requestHasher;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.objectMapper = objectMapper;
    }

    public FreezeResponse freeze(
            Long userId,
            Long accountId,
            String idempotencyKey,
            FreezeRequest request
    ) {
        FreezeRequest validated = validate(accountId, idempotencyKey, request);
        String requestHash = requestHasher.hashFreeze(
                accountId,
                validated.amount(),
                validated.businessType(),
                validated.remark()
        );
        try {
            return executor.execute(userId, accountId, idempotencyKey, requestHash, validated);
        } catch (DuplicateKeyException exception) {
            return replay(userId, idempotencyKey, requestHash);
        }
    }

    private FreezeRequest validate(Long accountId, String key, FreezeRequest request) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidFreezeRequestException("Account id must be positive");
        }
        if (key == null || key.isBlank() || key.length() > 128 || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidFreezeRequestException("Idempotency-Key must be 1-128 visible ASCII characters");
        }
        if (request == null) {
            throw new InvalidFreezeRequestException("Freeze request is required");
        }
        BigDecimal amount = MoneyAmounts.requirePositive(request.amount());
        String businessType = request.businessType();
        if (businessType == null || businessType.length() > 30
                || !BUSINESS_TYPE.matcher(businessType).matches()) {
            throw new InvalidFreezeRequestException("Business type must use uppercase letters, numbers or underscores");
        }
        if (request.remark() != null && request.remark().length() > 255) {
            throw new InvalidFreezeRequestException("Remark must not exceed 255 characters");
        }
        return new FreezeRequest(amount, businessType, request.remark());
    }

    private FreezeResponse replay(Long userId, String key, String requestHash) {
        IdempotencyRecordEntity record = idempotencyRecordMapper.selectOne(
                new LambdaQueryWrapper<IdempotencyRecordEntity>()
                        .eq(IdempotencyRecordEntity::getUserId, userId)
                        .eq(IdempotencyRecordEntity::getBusinessType, FreezeExecutor.IDEMPOTENCY_BUSINESS_TYPE)
                        .eq(IdempotencyRecordEntity::getIdempotencyKey, key)
        );
        if (record == null) {
            throw new IllegalStateException("Duplicate key was raised without a freeze idempotency record");
        }
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        if (!"SUCCESS".equals(record.getStatus()) || record.getResponseSnapshot() == null) {
            throw new IdempotencyRequestInProgressException();
        }
        try {
            return objectMapper.readValue(record.getResponseSnapshot(), FreezeResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored freeze idempotency response is invalid", exception);
        }
    }
}
