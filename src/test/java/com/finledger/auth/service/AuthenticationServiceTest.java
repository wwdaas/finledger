package com.finledger.auth.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.finledger.auth.dto.LoginRequest;
import com.finledger.auth.dto.LoginResponse;
import com.finledger.auth.exception.InvalidCredentialsException;
import com.finledger.user.entity.UserEntity;
import com.finledger.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService tokenService;

    @Test
    void shouldIssueBearerTokenForValidCredentials() {
        UserEntity user = user();
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn(new JwtTokenService.IssuedToken("signed.jwt", 3600));

        AuthenticationService service =
                new AuthenticationService(userMapper, passwordEncoder, tokenService);
        LoginResponse response = service.login(new LoginRequest("Alice", "Password123"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    void shouldRejectWrongPassword() {
        UserEntity user = user();
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        AuthenticationService service =
                new AuthenticationService(userMapper, passwordEncoder, tokenService);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return user;
    }
}
