package com.finledger.user.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String status,
        LocalDateTime createdAt
) {
}
