package com.smartqueue.ai.api;

import com.smartqueue.ai.api.dto.AiAnomalyResponse;
import com.smartqueue.ai.api.dto.AiEtaResponse;
import com.smartqueue.ai.api.dto.AiForecastResponse;
import com.smartqueue.ai.api.dto.AiRecommendationResponse;
import com.smartqueue.ai.application.AiService;
import com.smartqueue.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * GET /api/ai/eta/{tokenId} - Explainable AI-assisted ETA prediction for a token
     */
    @GetMapping("/eta/{tokenId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AiEtaResponse>> predictEta(@PathVariable UUID tokenId) {
        AiEtaResponse response = aiService.predictEta(tokenId);
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /**
     * GET /api/ai/forecast - Demand forecasting with confidence intervals
     */
    @GetMapping("/forecast")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AiForecastResponse>> forecastDemand(
            @RequestParam(required = false) UUID queueId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(defaultValue = "6") int horizonHours
    ) {
        AiForecastResponse response = aiService.forecastDemand(queueId, serviceId, horizonHours);
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /**
     * GET /api/ai/anomalies - Operational anomaly detection
     */
    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AiAnomalyResponse>> detectAnomalies(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID queueId
    ) {
        AiAnomalyResponse response = aiService.detectAnomalies(branchId, queueId);
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /**
     * GET /api/ai/recommendations - Evidence-based actionable recommendations
     */
    @GetMapping("/recommendations")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AiRecommendationResponse>> getRecommendations(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID queueId
    ) {
        AiRecommendationResponse response = aiService.getRecommendations(branchId, queueId);
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }
}
