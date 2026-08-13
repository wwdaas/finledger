package com.finledger.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "username must not be blank")
        @Size(min = 3, max = 50, message = "username length must be between 3 and 50")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]*$",
                message = "username must start with a letter and contain only letters, digits, or underscores"
        )
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$",
                message = "password must contain letters and digits and use visible ASCII characters"
        )
        String password
) {
}
