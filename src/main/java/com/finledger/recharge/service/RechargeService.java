package com.finledger.recharge.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountNotActiveException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.common.money.MoneyAmounts;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.generator.TransactionRecordNumberGenerator;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.recharge.dto.RechargeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RechargeService {

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final TransactionRecordMapper transactionRecordMapper;
    private final TransactionRecordNumberGenerator recordNumberGenerator;

    public RechargeService(
            AccountService accountService,
            AccountMapper accountMapper,
            TransactionRecordMapper transactionRecordMapper,
            TransactionRecordNumberGenerator recordNumberGenerator
    ) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.transactionRecordMapper = transactionRecordMapper;
        this.recordNumberGenerator = recordNumberGenerator;
    }

    @Transactional
    public RechargeResponse recharge(Long userId, Long accountId, BigDecimal requestedAmount) {
        BigDecimal amount = MoneyAmounts.requirePositive(requestedAmount);
        AccountEntity account = accountService.lockOwnedAccount(userId, accountId);
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new AccountNotActiveException(accountId, account.getStatus());
        }

        BigDecimal totalBefore = account.getTotalBalance();
        BigDecimal availableAfter = MoneyAmounts.requireValidBalance(
                account.getAvailableBalance().add(amount)
        );
        account.setAvailableBalance(availableAfter);
        BigDecimal totalAfter = MoneyAmounts.requireValidBalance(account.getTotalBalance());
        account.setVersion(account.getVersion() + 1);
        if (accountMapper.updateById(account) != 1) {
            throw new IllegalStateException("Expected one updated account row");
        }

        Long businessId = IdWorker.getId();
        TransactionRecordEntity record = new TransactionRecordEntity();
        record.setRecordNo(recordNumberGenerator.nextRecordNo());
        record.setAccountId(accountId);
        record.setUserId(userId);
        record.setBusinessType("RECHARGE");
        record.setBusinessId(businessId);
        record.setDirection("CREDIT");
        record.setAmount(amount);
        record.setCurrency(account.getCurrency());
        record.setBalanceBefore(totalBefore);
        record.setBalanceAfter(totalAfter);
        if (transactionRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("Expected one inserted transaction record");
        }

        return new RechargeResponse(
                businessId,
                record.getRecordNo(),
                accountId,
                amount,
                availableAfter,
                account.getFrozenBalance(),
                totalAfter
        );
    }
}
