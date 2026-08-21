package com.finledger.freeze.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.common.money.InvalidAmountException;
import com.finledger.freeze.dto.FreezeRequest;
import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.exception.InvalidFreezeRequestException;
import com.finledger.idempotency.mapper.IdempotencyRecordMapper;
import com.finledger.idempotency.service.IdempotencyRequestHasher;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreezeServiceTest {

    @Mock private FreezeExecutor executor;
    @Mock private IdempotencyRecordMapper idempotencyRecordMapper;
    @Mock private ObjectMapper objectMapper;

    private FreezeService freezeService;

    @BeforeEach
    void setUp() {
        freezeService = new FreezeService(
                executor,
                new IdempotencyRequestHasher(),
                idempotencyRecordMapper,
                objectMapper
        );
    }

    @Test
    void shouldValidateAndNormalizeAmountBeforeExecuting() {
        FreezeResponse expected = response();
        when(executor.execute(eq(10L), eq(1L), eq("freeze-key"), any(), any()))
                .thenReturn(expected);

        FreezeResponse result = freezeService.freeze(
                10L,
                1L,
                "freeze-key",
                new FreezeRequest(new BigDecimal("300"), "TRADE", "Pending")
        );

        assertThat(result).isEqualTo(expected);
        verify(executor).execute(
                eq(10L),
                eq(1L),
                eq("freeze-key"),
                any(),
                eq(new FreezeRequest(new BigDecimal("300.00"), "TRADE", "Pending"))
        );
    }

    @Test
    void shouldRejectInvalidAmountAndBusinessTypeBeforeExecution() {
        assertThatThrownBy(() -> freezeService.freeze(
                10L,
                1L,
                "freeze-key",
                new FreezeRequest(BigDecimal.ZERO, "TRADE", null)
        )).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> freezeService.freeze(
                10L,
                1L,
                "freeze-key",
                new FreezeRequest(new BigDecimal("1.00"), "trade", null)
        )).isInstanceOf(InvalidFreezeRequestException.class);

        verify(executor, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectInvalidIdempotencyKeyBeforeHashingOrExecuting() {
        assertThatThrownBy(() -> freezeService.freeze(
                10L,
                1L,
                "contains space",
                new FreezeRequest(new BigDecimal("1.00"), "TRADE", null)
        )).isInstanceOf(InvalidFreezeRequestException.class);

        verify(executor, never()).execute(any(), any(), any(), any(), any());
    }

    private FreezeResponse response() {
        return new FreezeResponse(
                99L,
                "FRZ001",
                1L,
                new BigDecimal("300.00"),
                "TRADE",
                "Pending",
                "FROZEN",
                new BigDecimal("700.00"),
                new BigDecimal("300.00"),
                new BigDecimal("1000.00"),
                LocalDateTime.parse("2026-08-13T10:00:00")
        );
    }
}
