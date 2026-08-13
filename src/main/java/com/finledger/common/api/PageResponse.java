package com.finledger.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long page,
        long size,
        long total,
        long totalPages
) {
}
