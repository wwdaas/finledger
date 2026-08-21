package com.finledger.freeze.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.account.entity.AccountEntity;
import com.finledger.account.exception.InsufficientAvailableBalanceException;
import com.finledger.account.mapper.AccountMapper;
import com.finledger.account.service.AccountService;
import com.finledger.freeze.dto.FreezeRequest;
import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.entity.FundFreezeEntity;
import com.finledger.freeze.generator.FreezeNumberGenerator;
import com.finledger.freeze.mapper.FundFreezeMapper;
import com.finledger.idempotency.entity.IdempotencyRecordEntity;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.settlement.service.FundMovementRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class FreezeExecutorTest {

    @Mock private AccountService accountService;
    @Mock private AccountMapper accountMapper;
    @Mock private FundFreezeMapper freezeMapper;
    @Mock private FreezeNumberGenerator numberGenerator;
    @Mock private FundMovementRecorder movementRecorder;
    @Mock private IdempotencyRecordMapper idempotencyRecordMapper;
    @Mock private ObjectMapper objectMapper;

    private FreezeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FreezeExecutor(
                accountService,
                accountMapper,
                freezeMapper,
                numberGenerator,
                movementRecorder,
                idempotencyRecordMapper,
                objectMapper
        );
    }

    @Test
    void shouldMoveAvailableToFrozenAndWriteBusinessRecords() throws Exception {
        AccountEntity account = account("1000.00", "0.00");
        FreezeRequest request = new FreezeRequest(new BigDecimal("300.00"), "TRADE", "Pending");
        when(idempotencyRecordMapper.insert(any(IdempotencyRecordEntity.class))).thenReturn(1);
        when(accountService.lockOwnedAccount(10L, 1L)).thenReturn(account);
        when(accountMapper.moveAvailableToFrozen(1L, new BigDecimal("300.00"))).thenReturn(1);
        when(numberGenerator.nextFreezeNo()).thenReturn("FRZ001");
        doAnswer(invocation -> {
            FundFreezeEntity freeze = invocation.getArgument(0);
            freeze.setId(99L);
            return 1;
        }).when(freezeMapper).insert(any(FundFreezeEntity.class));
        when(objectMapper.writeValueAsString(any(FreezeResponse.class))).thenReturn("{}");
        when(idempotencyRecordMapper.updateById(any(IdempotencyRecordEntity.class))).thenReturn(1);

        FreezeResponse response = executor.execute(10L, 1L, "key", "hash", request);

        assertThat(response.freezeId()).isEqualTo(99L);
        assertThat(response.availableBalance()).isEqualByComparingTo("700.00");
        assertThat(response.frozenBalance()).isEqualByComparingTo("300.00");
        assertThat(response.totalBalance()).isEqualByComparingTo("1000.00");
        verify(accountService).lockOwnedAccount(10L, 1L);
        verify(movementRecorder).record(
                account,
                "FUND_FREEZE",
                99L,
                "FREEZE",
                new BigDecimal("300.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00")
        );
        ArgumentCaptor<IdempotencyRecordEntity> claim =
                ArgumentCaptor.forClass(IdempotencyRecordEntity.class);
        verify(idempotencyRecordMapper).updateById(claim.capture());
        assertThat(claim.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(claim.getValue().getResourceId()).isEqualTo(99L);
    }

    @Test
    void shouldRejectInsufficientLockedBalanceBeforeUpdate() {
        AccountEntity account = account("100.00", "900.00");
        when(idempotencyRecordMapper.insert(any(IdempotencyRecordEntity.class))).thenReturn(1);
        when(accountService.lockOwnedAccount(10L, 1L)).thenReturn(account);

        assertThatThrownBy(() -> executor.execute(
                10L,
                1L,
                "key",
                "hash",
                new FreezeRequest(new BigDecimal("300.00"), "TRADE", null)
        )).isInstanceOf(InsufficientAvailableBalanceException.class);

        verify(accountMapper, never()).moveAvailableToFrozen(any(), any());
        verify(freezeMapper, never()).insert(any(FundFreezeEntity.class));
        verify(movementRecorder, never()).record(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void shouldTreatConditionalUpdateFailureAsInsufficientBalance() {
        AccountEntity account = account("1000.00", "0.00");
        when(idempotencyRecordMapper.insert(any(IdempotencyRecordEntity.class))).thenReturn(1);
        when(accountService.lockOwnedAccount(10L, 1L)).thenReturn(account);
        when(accountMapper.moveAvailableToFrozen(1L, new BigDecimal("300.00"))).thenReturn(0);

        assertThatThrownBy(() -> executor.execute(
                10L,
                1L,
                "key",
                "hash",
                new FreezeRequest(new BigDecimal("300.00"), "TRADE", null)
        )).isInstanceOf(InsufficientAvailableBalanceException.class);

        verify(freezeMapper, never()).insert(any(FundFreezeEntity.class));
    }

    private AccountEntity account(String available, String frozen) {
        AccountEntity account = new AccountEntity();
        account.setId(1L);
        account.setUserId(10L);
        account.setAvailableBalance(new BigDecimal(available));
        account.setFrozenBalance(new BigDecimal(frozen));
        account.setCurrency("CNY");
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        return account;
    }
}
