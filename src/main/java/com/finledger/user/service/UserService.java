package com.finledger.user.service;

import com.finledger.user.dto.RegisterUserRequest;
import com.finledger.user.dto.UserResponse;
import com.finledger.user.entity.UserEntity;
import com.finledger.user.exception.UserDisabledException;
import com.finledger.user.exception.UserNotFoundException;
import com.finledger.user.exception.UsernameAlreadyExistsException;
import com.finledger.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterUserRequest request) {
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);

        UserEntity user = new UserEntity();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(ACTIVE_STATUS);

        try {
            int insertedRows = userMapper.insert(user);
            if (insertedRows != 1) {
                throw new IllegalStateException("Expected one inserted user row, got " + insertedRows);
            }
        } catch (DuplicateKeyException exception) {
            throw new UsernameAlreadyExistsException(normalizedUsername, exception);
        }

        return getById(user.getId());
    }

    public UserResponse getById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        return toResponse(user);
    }

    public UserResponse requireActiveUser(Long userId) {
        UserResponse user = getById(userId);
        if (!ACTIVE_STATUS.equals(user.status())) {
            throw new UserDisabledException(userId);
        }
        return user;
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
