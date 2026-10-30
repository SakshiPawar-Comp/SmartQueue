package com.smartqueue.analytics.api.dto;

import java.util.UUID;

public record ServiceAnalyticsResponse(
        UUID serviceId,
        String serviceName,
        String branchName,
        long totalTokens,
        long completedTokens,
        long cancelledTokens,
        long skippedTokens,
        double avgWaitMinutes,
        double avgServiceMinutes,
        double completionRate
) {}
