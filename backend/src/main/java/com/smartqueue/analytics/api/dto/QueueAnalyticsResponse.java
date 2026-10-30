package com.smartqueue.analytics.api.dto;

import java.util.UUID;

public record QueueAnalyticsResponse(
        UUID queueId,
        String queueName,
        String tokenPrefix,
        String branchName,
        String serviceName,
        long totalTokens,
        long completedTokens,
        long cancelledTokens,
        long skippedTokens,
        long currentWaiting,
        double avgWaitMinutes,
        double avgServiceMinutes,
        int peakHour,
        double throughputPerHour
) {}
