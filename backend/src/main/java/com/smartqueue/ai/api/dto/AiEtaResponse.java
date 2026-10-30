package com.smartqueue.ai.api.dto;

import java.util.Map;

public record AiEtaResponse(
        int position,
        int estimatedWaitMinutes,
        String predictionType,
        Double confidenceScore,
        Map<String, Object> factors,
        String modelVersion
) {}
