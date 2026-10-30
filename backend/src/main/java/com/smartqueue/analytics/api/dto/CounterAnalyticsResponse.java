package com.smartqueue.analytics.api.dto;

import java.util.UUID;

public record CounterAnalyticsResponse(
        UUID counterId,
        String counterName,
        String counterCode,
        String branchName,
        long tokensServed,
        double avgServiceMinutes,
        double avgWaitBeforeServiceMinutes,
        double throughputPerHour
) {}
