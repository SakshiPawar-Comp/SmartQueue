package com.smartqueue.analytics.api.dto;

public record PeakHourResponse(
        int hourOfDay,
        long tokenCount,
        long completedCount,
        double avgWaitMinutes,
        double avgServiceMinutes
) {}
