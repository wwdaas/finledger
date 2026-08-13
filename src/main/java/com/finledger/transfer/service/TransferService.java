package com.finledger.transfer.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountNotActiveException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.LockedTransferAccounts;
import com.finledger.account.service.AccountService;
import com.finledger.common.money.MoneyAmounts;
import com.finledger.ledger.entity.TransactionRecordEntity;
import com.finledger.ledger.generator.TransactionRecordNumberGenerator;
import com.finledger.ledger.mapper.TransactionRecordMapper;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.exception.CurrencyMismatchException;
import com.finledger.transfer.exception.InsufficientBalanceException;
import com.finledger.transfer.exception.SameAccountTransferException;
import com.finledger.transfer.generator.TransferNumberGenerator;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TransferService {

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final TransactionRecordMapper transactionRecordMapper;
    private final TransferNumberGenerator transferNumberGenerator;
    private final TransactionRecordNumberGenerator recordNumberGenerator;

    public TransferService(
            AccountService accountService,
            AccountMapper accountMapper,
            TransferOrderMapper transferOrderMapper,
            TransactionRecordMapper transactionRecordMapper,
            TransferNumberGenerator transferNumberGenerator,
            TransactionRecordNumberGenerator recordNumberGenerator
    ) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.transferOrderMapper = transferOrderMapper;
        this.transactionRecordMapper = transactionRecordMapper;
        this.transferNumberGenerator = transferNumberGenerator;
        this.recordNumberGenerator = recordNumberGenerator;
    }

    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SameAccountTransferException();
        }
        BigDecimal amount = MoneyAmounts.requirePositive(request.amount());
        LockedTransferAccounts lockedAccounts = accountService.lockTransferAccounts(
                userId, request.fromAccountId(), request.toAccountId()
        );
        AccountEntity fromAccount = lockedAccounts.fromAccount();
        AccountEntity toAccount = lockedAccounts.toAccount();

        requireActive(fromAccount);
        requireActive(toAccount);
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new CurrencyMismatchException();
        }
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(fromAccount.getId());
        }

        BigDecimal fromBefore = fromAccount.getBalance();
        BigDecimal toBefore = toAccount.getBalance();
        BigDecimal fromAfter = MoneyAmounts.requireValidBalance(fromBefore.subtract(amount));
        BigDecimal toAfter = MoneyAmounts.requireValidBalance(toBefore.add(amount));

        updateBalance(fromAccount, fromAfter);
        updateBalance(toAccount, toAfter);

        LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
        TransferOrderEntity order = createOrder(userId, request, amount, fromAccount.getCurrency(), completedAt);
        insertTransferRecord(order, fromAccount, toAccount.getId(), "DEBIT", fromBefore, fromAfter);
        insertTransferRecord(order, toAccount, fromAccount.getId(), "CREDIT", toBefore, toAfter);

        return new TransferResponse(
                order.getId(), order.getTransferNo(), fromAccount.getId(), toAccount.getId(),
                amount, order.getCurrency(), order.getStatus(), fromAfter, toAfter, completedAt
        );
    }

    private void requireActive(AccountEntity account) {
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
    }

    private void updateBalance(AccountEntity account, BigDecimal newBalance) {
        account.setBalance(newBalance);
        account.setVersion(account.getVersion() + 1);
        if (accountMapper.updateById(account) != 1) {
            throw new IllegalStateException("Expected one updated account row: " + account.getId());
        }
    }

    private TransferOrderEntity createOrder(
            Long userId,
            TransferRequest request,
            BigDecimal amount,
            String currency,
            LocalDateTime completedAt
    ) {
        TransferOrderEntity order = new TransferOrderEntity();
        order.setTransferNo(transferNumberGenerator.nextTransferNo());
        order.setInitiatorUserId(userId);
        order.setFromAccountId(request.fromAccountId());
        order.setToAccountId(request.toAccountId());
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setStatus("SUCCESS");
        order.setCompletedAt(completedAt);
        if (transferOrderMapper.insert(order) != 1) {
            throw new IllegalStateException("Expected one inserted transfer order");
        }
        return order;
    }

    private void insertTransferRecord(
            TransferOrderEntity order,
            AccountEntity account,
            Long counterpartyAccountId,
            String direction,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
        TransactionRecordEntity record = new TransactionRecordEntity();
        record.setRecordNo(recordNumberGenerator.nextRecordNo());
        record.setAccountId(account.getId());
        record.setUserId(account.getUserId());
        record.setBusinessType("TRANSFER");
        record.setBusinessId(order.getId());
        record.setDirection(direction);
        record.setAmount(order.getAmount());
        record.setCurrency(order.getCurrency());
        record.setBalanceBefore(balanceBefore);
        record.setBalanceAfter(balanceAfter);
        record.setCounterpartyAccountId(counterpartyAccountId);
        if (transactionRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("Expected one inserted transaction record");
        }
    }
}
