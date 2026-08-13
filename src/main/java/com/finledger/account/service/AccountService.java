package com.finledger.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finledger.account.dto.AccountResponse;
import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountAccessDeniedException;
import com.finledger.account.exception.AccountNotFoundException;
import com.finledger.account.generator.AccountNumberGenerator;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.user.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private static final int ACCOUNT_NUMBER_ATTEMPTS = 3;
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CNY = "CNY";

    private final AccountMapper accountMapper;
    private final AccountNumberGenerator accountNumberGenerator;
    private final UserService userService;

    public AccountService(
            AccountMapper accountMapper,
            AccountNumberGenerator accountNumberGenerator,
            UserService userService
    ) {
        this.accountMapper = accountMapper;
        this.accountNumberGenerator = accountNumberGenerator;
        this.userService = userService;
    }

    public AccountResponse create(Long userId) {
        userService.requireActiveUser(userId);

        for (int attempt = 1; attempt <= ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            AccountEntity account = new AccountEntity();
            account.setUserId(userId);
            account.setAccountNo(accountNumberGenerator.nextAccountNo());
            account.setBalance(BigDecimal.ZERO.setScale(2));
            account.setAvailableBalance(BigDecimal.ZERO.setScale(2));
            account.setFrozenBalance(BigDecimal.ZERO.setScale(2));
            account.setCurrency(CNY);
            account.setStatus(ACTIVE_STATUS);
            account.setVersion(0L);

            try {
                accountMapper.insert(account);
                return toResponse(requireAccount(account.getId()));
            } catch (DuplicateKeyException duplicateKeyException) {
                if (attempt == ACCOUNT_NUMBER_ATTEMPTS) {
                    throw duplicateKeyException;
                }
            }
        }
        throw new IllegalStateException("Account number generation attempts exhausted");
    }

    public List<AccountResponse> listByUser(Long userId) {
        userService.getById(userId);
        return accountMapper.selectList(
                        Wrappers.<AccountEntity>lambdaQuery()
                                .eq(AccountEntity::getUserId, userId)
                                .orderByDesc(AccountEntity::getId)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    public AccountResponse getOwnedAccount(Long userId, Long accountId) {
        AccountEntity account = requireOwnedAccount(userId, accountId);
        return toResponse(account);
    }

    public AccountEntity requireOwnedAccount(Long userId, Long accountId) {
        AccountEntity account = requireAccount(accountId);
        if (!account.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException(accountId);
        }
        return account;
    }

    public AccountEntity lockOwnedAccount(Long userId, Long accountId) {
        AccountEntity account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        if (!account.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException(accountId);
        }
        return account;
    }

    public LockedTransferAccounts lockTransferAccounts(
            Long userId,
            Long fromAccountId,
            Long toAccountId
    ) {
        Long lowerId = Math.min(fromAccountId, toAccountId);
        Long higherId = Math.max(fromAccountId, toAccountId);

        AccountEntity lowerAccount = lockAccount(lowerId);
        AccountEntity higherAccount = lockAccount(higherId);
        AccountEntity fromAccount = fromAccountId.equals(lowerId) ? lowerAccount : higherAccount;
        AccountEntity toAccount = toAccountId.equals(lowerId) ? lowerAccount : higherAccount;

        if (!fromAccount.getUserId().equals(userId)) {
            throw new AccountAccessDeniedException(fromAccountId);
        }
        return new LockedTransferAccounts(fromAccount, toAccount);
    }

    private AccountEntity lockAccount(Long accountId) {
        AccountEntity account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    public AccountEntity requireAccount(Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    private AccountResponse toResponse(AccountEntity account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNo(),
                account.getBalance(),
                account.getAvailableBalance(),
                account.getFrozenBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getVersion(),
                account.getCreatedAt()
        );
    }
}
