package com.smartqueue.ai.api.dto;

import java.util.List;
import java.util.UUID;

public record AiAnomalyResponse(
        List<AnomalyItemDto> anomalies,
        String modelVersion
) {
    public record AnomalyItemDto(
            String type,
            String severity,
            UUID queueId,
            String queueName,
            double observedValue,
            double baselineValue,
            double deviationFactor,
            String message,
            String timestamp
    ) {}
}
