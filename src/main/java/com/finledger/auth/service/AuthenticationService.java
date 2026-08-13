package com.finledger.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finledger.auth.dto.LoginRequest;
import com.finledger.auth.dto.LoginResponse;
import com.finledger.auth.exception.InvalidCredentialsException;
import com.finledger.user.entity.UserEntity;
import com.finledger.user.exception.UserDisabledException;
import com.finledger.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthenticationService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthenticationService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().toLowerCase(Locale.ROOT);
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username)
        );
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UserDisabledException(user.getId());
        }
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}
