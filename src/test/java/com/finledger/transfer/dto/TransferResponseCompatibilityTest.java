package com.finledger.transfer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferResponseCompatibilityTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldReplayLegacyIdempotencySnapshotWithExplicitTotalBalanceSemantics() throws Exception {
        String legacySnapshot = """
                {
                  "transferId": 9,
                  "transferNo": "TF009",
                  "fromAccountId": 1,
                  "toAccountId": 2,
                  "amount": 10.00,
                  "currency": "CNY",
                  "status": "SUCCESS",
                  "fromBalance": 90.00,
                  "toBalance": 110.00,
                  "completedAt": "2026-08-13T12:00:00"
                }
                """;

        TransferResponse response = objectMapper.readValue(legacySnapshot, TransferResponse.class);

        assertThat(response.fromTotalBalance()).isEqualByComparingTo("90.00");
        assertThat(response.toTotalBalance()).isEqualByComparingTo("110.00");
        assertThat(objectMapper.writeValueAsString(response))
                .contains("\"fromBalance\":90.00", "\"toBalance\":110.00")
                .doesNotContain("fromTotalBalance", "toTotalBalance");
    }
}
