package com.finledger.account.service;

import com.finledger.account.dto.AccountResponse;
import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.AccountAccessDeniedException;
import com.finledger.account.generator.AccountNumberGenerator;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.user.dto.UserResponse;
import com.finledger.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private UserService userService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountMapper, accountNumberGenerator, userService);
    }

    @Test
    void shouldCreateZeroBalanceAccount() {
        when(userService.requireActiveUser(1L))
                .thenReturn(new UserResponse(1L, "alice", "ACTIVE", LocalDateTime.now()));
        when(accountNumberGenerator.nextAccountNo()).thenReturn("FLACCOUNT001");
        doAnswer(invocation -> {
            AccountEntity account = invocation.getArgument(0);
            account.setId(10L);
            return 1;
        }).when(accountMapper).insert(any(AccountEntity.class));
        when(accountMapper.selectById(10L)).thenReturn(account(10L, 1L));

        AccountResponse response = accountService.create(1L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.balance()).isEqualByComparingTo("0.00");
        assertThat(response.currency()).isEqualTo("CNY");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectAccessToAnotherUsersAccount() {
        when(accountMapper.selectById(10L)).thenReturn(account(10L, 2L));

        assertThatThrownBy(() -> accountService.getOwnedAccount(1L, 10L))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void shouldLockTransferAccountsInAscendingIdOrder() {
        AccountEntity lower = account(10L, 2L);
        AccountEntity higher = account(20L, 1L);
        when(accountMapper.selectByIdForUpdate(10L)).thenReturn(lower);
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(higher);

        LockedTransferAccounts result = accountService.lockTransferAccounts(1L, 20L, 10L);

        org.mockito.InOrder lockOrder = inOrder(accountMapper);
        lockOrder.verify(accountMapper).selectByIdForUpdate(10L);
        lockOrder.verify(accountMapper).selectByIdForUpdate(20L);
        assertThat(result.fromAccount().getId()).isEqualTo(20L);
        assertThat(result.toAccount().getId()).isEqualTo(10L);
    }

    private AccountEntity account(Long id, Long userId) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setUserId(userId);
        account.setAccountNo("FLACCOUNT001");
        account.setBalance(new BigDecimal("0.00"));
        account.setCurrency("CNY");
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return account;
    }
}
