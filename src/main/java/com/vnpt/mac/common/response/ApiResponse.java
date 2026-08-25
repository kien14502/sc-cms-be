package com.vnpt.mac.common.response;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(T data, Meta meta) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, new Meta(UUID.randomUUID().toString(), Instant.now()));
    }

    public record Meta(String requestId, Instant timestamp) {}
}
