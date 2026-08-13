package com.finledger.transfer.service;

import com.finledger.idempotency.service.IdempotencyReplayService;
import com.finledger.idempotency.service.IdempotencyRequestHasher;
import com.finledger.transfer.dto.TransferRequest;
import com.finledger.transfer.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentTransferServiceTest {

    @Mock private IdempotentTransferExecutor executor;
    @Mock private IdempotencyReplayService replayService;
    @Mock private IdempotencyRequestHasher requestHasher;

    @Test
    void shouldReplayCommittedResponseWhenDatabaseRejectsDuplicateClaim() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("10.00"));
        TransferResponse response = response();
        when(requestHasher.hashTransfer(request)).thenReturn("hash");
        when(executor.execute(7L, "key-1", "hash", request))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(replayService.replay(7L, "key-1", "hash")).thenReturn(response);

        IdempotentTransferService service =
                new IdempotentTransferService(executor, replayService, requestHasher);

        assertThat(service.transfer(7L, "key-1", request)).isSameAs(response);
    }

    private TransferResponse response() {
        return new TransferResponse(
                9L, "TF009", 1L, 2L, new BigDecimal("10.00"), "CNY", "SUCCESS",
                new BigDecimal("90.00"), new BigDecimal("10.00"), LocalDateTime.now()
        );
    }
}
