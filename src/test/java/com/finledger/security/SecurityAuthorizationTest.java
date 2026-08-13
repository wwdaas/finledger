package com.finledger.security;

import com.finledger.account.controller.AccountController;
import com.finledger.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(AccountController.class)
@Import(SecurityConfiguration.class)
class SecurityAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AccountService accountService;

    @Test
    void shouldRejectMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturnJsonForInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer broken.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldUseVerifiedJwtSubjectAsUserIdentity() throws Exception {
        mockMvc.perform(post("/api/accounts").with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isCreated());

        verify(accountService).create(42L);
    }
}
