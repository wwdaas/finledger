package com.finledger.freeze.controller;

import com.finledger.freeze.dto.FreezeResponse;
import com.finledger.freeze.service.FreezeService;
import com.finledger.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FreezeController.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("test")
class FreezeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FreezeService freezeService;

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/accounts/1/freezes")
                        .header("Idempotency-Key", "freeze-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":300.00,"businessType":"TRADE"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldUseJwtSubjectAndReturnCreatedFreeze() throws Exception {
        when(freezeService.freeze(eq(42L), eq(1L), eq("freeze-key"), any()))
                .thenReturn(response());

        mockMvc.perform(post("/api/accounts/1/freezes")
                        .with(jwt().jwt(token -> token.subject("42")))
                        .header("Idempotency-Key", "freeze-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 300.00,
                                  "businessType": "TRADE",
                                  "remark": "Pending transaction"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.freezeNo").value("FRZ001"))
                .andExpect(jsonPath("$.availableBalance").value(700.00))
                .andExpect(jsonPath("$.frozenBalance").value(300.00))
                .andExpect(jsonPath("$.totalBalance").value(1000.00));

        verify(freezeService).freeze(eq(42L), eq(1L), eq("freeze-key"), any());
    }

    private FreezeResponse response() {
        return new FreezeResponse(
                99L,
                "FRZ001",
                1L,
                new BigDecimal("300.00"),
                "TRADE",
                "Pending transaction",
                "FROZEN",
                new BigDecimal("700.00"),
                new BigDecimal("300.00"),
                new BigDecimal("1000.00"),
                LocalDateTime.parse("2026-08-13T10:00:00")
        );
    }
}
