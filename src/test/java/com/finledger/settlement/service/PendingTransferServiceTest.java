package com.finledger.settlement.service;

import com.finledger.account.entity.AccountEntity;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.account.service.LockedTransferAccounts;
import com.finledger.settlement.exception.InsufficientAvailableBalanceException;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.entity.TransferOrderEntity;
import com.finledger.transfer.generator.TransferNumberGenerator;
import com.finledger.transfer.mapper.TransferOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingTransferServiceTest {

    @Mock private AccountService accountService;
    @Mock private AccountMapper accountMapper;
    @Mock private TransferOrderMapper transferOrderMapper;
    @Mock private TransferNumberGenerator transferNumberGenerator;
    @Mock private FundMovementRecorder movementRecorder;

    private PendingTransferService pendingTransferService;

    @BeforeEach
    void setUp() {
        pendingTransferService = new PendingTransferService(
                accountService, accountMapper, transferOrderMapper,
                transferNumberGenerator, movementRecorder
        );
    }

    @Test
    void shouldFreezeAvailableFundsWithoutChangingTotal() {
        AccountEntity from = account(1L, 10L, "1000.00");
        AccountEntity to = account(2L, 20L, "0.00");
        when(accountService.lockTransferAccounts(10L, 1L, 2L))
                .thenReturn(new LockedTransferAccounts(from, to));
        when(transferNumberGenerator.nextTransferNo()).thenReturn("TF-PENDING-1");
        doAnswer(invocation -> {
            TransferOrderEntity order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        }).when(transferOrderMapper).insert(any(TransferOrderEntity.class));
        when(accountMapper.updateById(from)).thenReturn(1);

        var response = pendingTransferService.createPending(
                10L, new TransferRequest(1L, 2L, new BigDecimal("300.00"))
        );

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.availableBalance()).isEqualByComparingTo("700.00");
        assertThat(response.frozenBalance()).isEqualByComparingTo("300.00");
        verify(movementRecorder).record(
                from, 99L, "FREEZE", new BigDecimal("300.00"),
                new BigDecimal("1000.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00")
        );
    }

    @Test
    void shouldRejectFreezeWhenAvailableBalanceIsInsufficient() {
        AccountEntity from = account(1L, 10L, "100.00");
        AccountEntity to = account(2L, 20L, "0.00");
        when(accountService.lockTransferAccounts(10L, 1L, 2L))
                .thenReturn(new LockedTransferAccounts(from, to));

        assertThatThrownBy(() -> pendingTransferService.createPending(
                10L, new TransferRequest(1L, 2L, new BigDecimal("300.00"))
        )).isInstanceOf(InsufficientAvailableBalanceException.class);

        assertThat(from.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(from.getFrozenBalance()).isEqualByComparingTo("0.00");
        verify(transferOrderMapper, never()).insert(any(TransferOrderEntity.class));
        verify(accountMapper, never()).updateById(any(AccountEntity.class));
    }

    private AccountEntity account(Long id, Long userId, String balance) {
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setUserId(userId);
        account.setBalance(new BigDecimal(balance));
        account.setAvailableBalance(new BigDecimal(balance));
        account.setFrozenBalance(new BigDecimal("0.00"));
        account.setCurrency("CNY");
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        return account;
    }
}
