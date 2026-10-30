package com.smartqueue.analytics.api.dto;

import java.util.UUID;

public record OperationalOverviewResponse(
        long totalTokens,
        long completedTokens,
        long cancelledTokens,
        long skippedTokens,
        long waitingTokens,
        long servingTokens,
        double avgWaitMinutes,
        double medianWaitMinutes,
        double maxWaitMinutes,
        double avgServiceMinutes,
        double medianServiceMinutes,
        double maxServiceMinutes,
        double throughputPerHour,
        double completionRate,
        double cancellationRate,
        double skipRate
) {}
