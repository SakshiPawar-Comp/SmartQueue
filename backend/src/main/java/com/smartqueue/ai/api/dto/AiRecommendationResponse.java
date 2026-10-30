package com.smartqueue.ai.api.dto;

import java.util.List;
import java.util.UUID;

public record AiRecommendationResponse(
        List<RecommendationDto> recommendations,
        String modelVersion
) {
    public record RecommendationDto(
            String id,
            String category,
            String priority,
            String title,
            String description,
            String evidence,
            UUID targetQueueId
    ) {}
}
