package com.finledger.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finledger.account.service.AccountService;
import com.finledger.common.api.PageResponse;
import com.finledger.ledger.dto.TransactionRecordResponse;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.exception.InvalidTransactionFilterException;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class TransactionRecordService {

    private static final Set<String> BUSINESS_TYPES = Set.of("RECHARGE", "TRANSFER");
    private static final Set<String> DIRECTIONS = Set.of("DEBIT", "CREDIT");

    private final TransactionRecordMapper transactionRecordMapper;
    private final AccountService accountService;

    public TransactionRecordService(
            TransactionRecordMapper transactionRecordMapper,
            AccountService accountService
    ) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.accountService = accountService;
    }

    public PageResponse<TransactionRecordResponse> query(
            Long userId,
            Long accountId,
            String requestedBusinessType,
            String requestedDirection,
            LocalDateTime from,
            LocalDateTime to,
            long pageNumber,
            long pageSize
    ) {
        if (accountId != null) {
            accountService.requireOwnedAccount(userId, accountId);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransactionFilterException("from must not be after to");
        }

        String businessType = normalizeFilter(requestedBusinessType, BUSINESS_TYPES, "businessType");
        String direction = normalizeFilter(requestedDirection, DIRECTIONS, "direction");
        LambdaQueryWrapper<TransactionRecordEntity> query =
                new LambdaQueryWrapper<TransactionRecordEntity>()
                        .eq(TransactionRecordEntity::getUserId, userId)
                        .eq(accountId != null, TransactionRecordEntity::getAccountId, accountId)
                        .eq(businessType != null, TransactionRecordEntity::getBusinessType, businessType)
                        .eq(direction != null, TransactionRecordEntity::getDirection, direction)
                        .ge(from != null, TransactionRecordEntity::getCreatedAt, from)
                        .le(to != null, TransactionRecordEntity::getCreatedAt, to)
                        .orderByDesc(TransactionRecordEntity::getCreatedAt)
                        .orderByDesc(TransactionRecordEntity::getId);

        Page<TransactionRecordEntity> result = transactionRecordMapper.selectPage(
                new Page<>(pageNumber, pageSize), query
        );
        return new PageResponse<>(
                result.getRecords().stream().map(this::toResponse).toList(),
                result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()
        );
    }

    private String normalizeFilter(String value, Set<String> allowed, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidTransactionFilterException("Unsupported " + fieldName + ": " + value);
        }
        return normalized;
    }

    private TransactionRecordResponse toResponse(TransactionRecordEntity record) {
        return new TransactionRecordResponse(
                record.getId(), record.getRecordNo(), record.getAccountId(),
                record.getBusinessType(), record.getBusinessId(), record.getDirection(),
                record.getAmount(), record.getCurrency(), record.getBalanceBefore(),
                record.getBalanceAfter(), record.getCounterpartyAccountId(), record.getCreatedAt()
        );
    }
}
