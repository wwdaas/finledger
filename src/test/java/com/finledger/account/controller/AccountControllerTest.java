package com.finledger.account.controller;

import com.finledger.account.dto.AccountResponse;
import com.finledger.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.finledger.security.SecurityConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(SecurityConfiguration.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void shouldCreateAccountForCurrentUser() throws Exception {
        when(accountService.create(1L)).thenReturn(new AccountResponse(
                10L,
                "FLACCOUNT001",
                new BigDecimal("0.00"),
                "CNY",
                "ACTIVE",
                0L,
                LocalDateTime.parse("2026-08-13T10:00:00")
        ));

        mockMvc.perform(post("/api/accounts").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.currency").value("CNY"));
    }
}
