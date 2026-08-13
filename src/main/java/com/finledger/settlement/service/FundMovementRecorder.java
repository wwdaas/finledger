package com.finledger.settlement.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.settlement.entity.FundMovementRecordEntity;
import com.finledger.settlement.generator.FundMovementNumberGenerator;
import com.finledger.settlement.mapper.FundMovementRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FundMovementRecorder {

    private final FundMovementRecordMapper movementMapper;
    private final FundMovementNumberGenerator numberGenerator;

    public FundMovementRecorder(
            FundMovementRecordMapper movementMapper,
            FundMovementNumberGenerator numberGenerator
    ) {
        this.movementMapper = movementMapper;
        this.numberGenerator = numberGenerator;
    }

    public void record(
            AccountEntity account,
            Long businessId,
            String action,
            BigDecimal amount,
            BigDecimal availableBefore,
            BigDecimal frozenBefore,
            BigDecimal totalBefore
    ) {
        FundMovementRecordEntity record = new FundMovementRecordEntity();
        record.setMovementNo(numberGenerator.nextMovementNo());
        record.setAccountId(account.getId());
        record.setUserId(account.getUserId());
        record.setBusinessType("DEFERRED_TRANSFER");
        record.setBusinessId(businessId);
        record.setAction(action);
        record.setAmount(amount);
        record.setAvailableBefore(availableBefore);
        record.setAvailableAfter(account.getAvailableBalance());
        record.setFrozenBefore(frozenBefore);
        record.setFrozenAfter(account.getFrozenBalance());
        record.setTotalBefore(totalBefore);
        record.setTotalAfter(account.getTotalBalance());
        if (movementMapper.insert(record) != 1) {
            throw new IllegalStateException("Expected one inserted fund movement record");
        }
    }
}
