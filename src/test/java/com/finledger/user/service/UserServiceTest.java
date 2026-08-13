package com.finledger.user.service;

import com.finledger.user.dto.RegisterUserRequest;
import com.finledger.user.dto.UserResponse;
import com.finledger.user.entity.UserEntity;
import com.finledger.user.exception.UserNotFoundException;
import com.finledger.user.exception.UsernameAlreadyExistsException;
import com.finledger.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder);
    }

    @Test
    void shouldNormalizeUsernameAndStorePasswordHash() {
        when(passwordEncoder.encode("Password123!")).thenReturn("bcrypt-hash");
        doAnswer(invocation -> {
            UserEntity insertedUser = invocation.getArgument(0);
            insertedUser.setId(42L);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));

        UserEntity storedUser = userEntity(42L, "alice_01", "bcrypt-hash");
        when(userMapper.selectById(42L)).thenReturn(storedUser);

        UserResponse response = userService.register(
                new RegisterUserRequest("Alice_01", "Password123!")
        );

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice_01");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(response.username()).isEqualTo("alice_01");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldTranslateDuplicateUsernameError() {
        when(passwordEncoder.encode("Password123!")).thenReturn("bcrypt-hash");
        when(userMapper.insert(any(UserEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate username"));

        assertThatThrownBy(() -> userService.register(
                new RegisterUserRequest("Alice_01", "Password123!")
        )).isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void shouldRejectUnknownUserId() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getById(999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    private UserEntity userEntity(Long id, String username, String passwordHash) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.parse("2026-08-13T10:00:00"));
        user.setUpdatedAt(LocalDateTime.parse("2026-08-13T10:00:00"));
        return user;
    }
}
