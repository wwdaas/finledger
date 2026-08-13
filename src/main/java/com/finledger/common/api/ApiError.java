package com.finledger.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<ApiFieldError> fieldErrors
) {
    public static ApiError of(
            int status,
            String error,
            String code,
            String message,
            String path
    ) {
        return new ApiError(Instant.now(), status, error, code, message, path, List.of());
    }
}
