package com.smartqueue.common.api;

public record ApiResponse<T>(
        T data,
        ApiError error,
        String correlationId
) {

    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(data, null, correlationId);
    }

    public record ApiError(
            String code,
            String message,
            Object details
    ) {
    }
}
