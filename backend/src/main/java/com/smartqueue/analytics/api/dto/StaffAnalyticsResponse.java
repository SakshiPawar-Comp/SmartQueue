package com.smartqueue.analytics.api.dto;

import java.util.UUID;

public record StaffAnalyticsResponse(
        UUID userId,
        String fullName,
        String email,
        long tokensServed,
        long completedCount,
        long skippedCount,
        double avgServiceMinutes
) {}
