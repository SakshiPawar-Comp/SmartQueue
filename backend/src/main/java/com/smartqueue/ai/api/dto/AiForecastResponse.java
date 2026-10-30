package com.smartqueue.ai.api.dto;

import java.util.List;

public record AiForecastResponse(
        String status,
        List<ForecastPointDto> forecast,
        String message,
        String modelVersion
) {
    public record ForecastPointDto(
            int hourOffset,
            int predictedCount,
            int lowerBound,
            int upperBound
    ) {}
}
