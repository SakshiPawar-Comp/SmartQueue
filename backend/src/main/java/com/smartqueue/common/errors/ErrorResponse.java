package com.smartqueue.common.errors;

import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, String> details
) {
}
