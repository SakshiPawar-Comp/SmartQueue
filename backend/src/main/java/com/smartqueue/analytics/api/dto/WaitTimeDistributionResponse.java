package com.smartqueue.analytics.api.dto;

public record WaitTimeDistributionResponse(
        double p50Minutes,
        double p75Minutes,
        double p90Minutes,
        double p95Minutes,
        double avgWaitMinutes,
        double maxWaitMinutes,
        double normalPriorityAvgMinutes,
        double priorityAvgMinutes,
        double vipPriorityAvgMinutes
) {}
