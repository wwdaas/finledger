package com.finledger.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finledger.account.entity.AccountEntity;
import com.finledger.account.service.AccountService;
import com.finledger.settlement.dto.DeferredTransferResponse;
import com.finledger.settlement.exception.TransactionNotFoundException;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.springframework.stereotype.Service;

@Service
public class DeferredTransferQueryService {

    private final TransferOrderMapper transferOrderMapper;
    private final AccountService accountService;

    public DeferredTransferQueryService(
            TransferOrderMapper transferOrderMapper,
            AccountService accountService
    ) {
        this.transferOrderMapper = transferOrderMapper;
        this.accountService = accountService;
    }

    public DeferredTransferResponse getOwnedById(Long userId, Long transferId) {
        TransferOrderEntity order = transferOrderMapper.selectOne(
                new LambdaQueryWrapper<TransferOrderEntity>()
                        .eq(TransferOrderEntity::getId, transferId)
                        .eq(TransferOrderEntity::getInitiatorUserId, userId)
                        .eq(TransferOrderEntity::getOrderType, "DEFERRED")
        );
        if (order == null) {
            throw new TransactionNotFoundException(transferId);
        }
        AccountEntity source = accountService.requireOwnedAccount(userId, order.getFromAccountId());
        return toResponse(order, source);
    }

    public DeferredTransferResponse getOwnedByNo(Long userId, String transferNo) {
        TransferOrderEntity order = transferOrderMapper.selectOne(
                new LambdaQueryWrapper<TransferOrderEntity>()
                        .eq(TransferOrderEntity::getTransferNo, transferNo)
                        .eq(TransferOrderEntity::getInitiatorUserId, userId)
                        .eq(TransferOrderEntity::getOrderType, "DEFERRED")
        );
        if (order == null) {
            throw new TransactionNotFoundException(transferNo);
        }
        AccountEntity source = accountService.requireOwnedAccount(userId, order.getFromAccountId());
        return toResponse(order, source);
    }

    private DeferredTransferResponse toResponse(
            TransferOrderEntity order,
            AccountEntity source
    ) {
        return new DeferredTransferResponse(
                order.getId(), order.getTransferNo(), order.getFromAccountId(), order.getToAccountId(),
                order.getAmount(), order.getCurrency(), order.getStatus(), order.getRiskDecision(),
                source.getBalance(), source.getAvailableBalance(), source.getFrozenBalance(),
                order.getCreatedAt(), order.getCompletedAt()
        );
    }
}
