package com.smartqueue.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient restClient;
    private final String internalSecret;

    public AiClient(
            @Value("${smartqueue.ai-service-url:http://localhost:8000}") String aiServiceUrl,
            @Value("${smartqueue.ai-service-secret:smartqueue-internal-ai-secret-2026}") String internalSecret
    ) {
        this.internalSecret = internalSecret;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000); // 2-second timeout
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Internal-API-Key", internalSecret)
                .build();
    }

    // --- ETA Request / Response DTOs ---
    public record EtaClientRequest(
            @JsonProperty("queueId") String queueId,
            @JsonProperty("tokenId") String tokenId,
            @JsonProperty("tokensAhead") int tokensAhead,
            @JsonProperty("defaultServiceTimeMinutes") int defaultServiceTimeMinutes,
            @JsonProperty("rollingAvgServiceMinutes") Double rollingAvgServiceMinutes,
            @JsonProperty("activeCountersCount") int activeCountersCount,
            @JsonProperty("priorityRank") int priorityRank,
            @JsonProperty("hourOfDay") int hourOfDay,
            @JsonProperty("dayOfWeek") int dayOfWeek
    ) {}

    public record EtaClientResponse(
            @JsonProperty("estimatedWaitMinutes") Integer estimatedWaitMinutes,
            @JsonProperty("predictionType") String predictionType,
            @JsonProperty("confidenceScore") Double confidenceScore,
            @JsonProperty("factors") Map<String, Object> factors,
            @JsonProperty("modelVersion") String modelVersion
    ) {}

    // --- Demand Forecast Request / Response DTOs ---
    public record HourlyPoint(
            @JsonProperty("date") String date,
            @JsonProperty("hour") int hour,
            @JsonProperty("tokenCount") int tokenCount
    ) {}

    public record ForecastClientRequest(
            @JsonProperty("queueId") String queueId,
            @JsonProperty("serviceId") String serviceId,
            @JsonProperty("horizonHours") int horizonHours,
            @JsonProperty("historicalHourlyVolumes") List<HourlyPoint> historicalHourlyVolumes
    ) {}

    public record ForecastPointClient(
            @JsonProperty("hourOffset") int hourOffset,
            @JsonProperty("predictedCount") int predictedCount,
            @JsonProperty("lowerBound") int lowerBound,
            @JsonProperty("upperBound") int upperBound
    ) {}

    public record ForecastClientResponse(
            @JsonProperty("status") String status,
            @JsonProperty("forecast") List<ForecastPointClient> forecast,
            @JsonProperty("message") String message,
            @JsonProperty("modelVersion") String modelVersion
    ) {}

    // --- Anomaly Request / Response DTOs ---
    public record MetricCheckClient(
            @JsonProperty("metricName") String metricName,
            @JsonProperty("observedValue") double observedValue,
            @JsonProperty("baselineMean") double baselineMean,
            @JsonProperty("baselineStdDev") double baselineStdDev,
            @JsonProperty("timestamp") String timestamp
    ) {}

    public record AnomalyClientRequest(
            @JsonProperty("queueId") String queueId,
            @JsonProperty("queueName") String queueName,
            @JsonProperty("metrics") List<MetricCheckClient> metrics
    ) {}

    public record AnomalyItemClient(
            @JsonProperty("type") String type,
            @JsonProperty("severity") String severity,
            @JsonProperty("queueId") String queueId,
            @JsonProperty("queueName") String queueName,
            @JsonProperty("observedValue") double observedValue,
            @JsonProperty("baselineValue") double baselineValue,
            @JsonProperty("deviationFactor") double deviationFactor,
            @JsonProperty("message") String message,
            @JsonProperty("timestamp") String timestamp
    ) {}

    public record AnomalyClientResponse(
            @JsonProperty("anomalies") List<AnomalyItemClient> anomalies,
            @JsonProperty("modelVersion") String modelVersion
    ) {}

    // --- Recommendation Request / Response DTOs ---
    public record RecommendationClientRequest(
            @JsonProperty("queueId") String queueId,
            @JsonProperty("queueName") String queueName,
            @JsonProperty("waitingCount") int waitingCount,
            @JsonProperty("averageWaitMinutes") double averageWaitMinutes,
            @JsonProperty("activeCounters") int activeCounters,
            @JsonProperty("cancellationRate") double cancellationRate,
            @JsonProperty("utilizationRate") double utilizationRate
    ) {}

    public record RecommendationItemClient(
            @JsonProperty("id") String id,
            @JsonProperty("category") String category,
            @JsonProperty("priority") String priority,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("evidence") String evidence,
            @JsonProperty("targetQueueId") String targetQueueId
    ) {}

    public record RecommendationClientResponse(
            @JsonProperty("recommendations") List<RecommendationItemClient> recommendations,
            @JsonProperty("modelVersion") String modelVersion
    ) {}

    // --- Document Verification Request / Response DTOs ---
    public record DocumentVerifyClientRequest(
            @JsonProperty("documentType") String documentType,
            @JsonProperty("fileName") String fileName,
            @JsonProperty("fileContentBase64") String fileContentBase64,
            @JsonProperty("fileTextContent") String fileTextContent,
            @JsonProperty("expectedHolderName") String expectedHolderName
    ) {}

    public record ExtractedDataClient(
            @JsonProperty("documentType") String documentType,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("holderName") String holderName,
            @JsonProperty("issuer") String issuer,
            @JsonProperty("issueDate") String issueDate,
            @JsonProperty("expiryDate") String expiryDate,
            @JsonProperty("isValidFormat") Boolean isValidFormat,
            @JsonProperty("readabilityScore") Double readabilityScore
    ) {}

    public record ValidationCheckClient(
            @JsonProperty("check") String check,
            @JsonProperty("status") String status,
            @JsonProperty("details") String details
    ) {}

    public record DocumentVerifyClientResponse(
            @JsonProperty("status") String status,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("summary") String summary,
            @JsonProperty("extractedData") ExtractedDataClient extractedData,
            @JsonProperty("validationChecks") List<ValidationCheckClient> validationChecks,
            @JsonProperty("modelVersion") String modelVersion
    ) {}

    /**
     * Call AI service for ETA prediction with strict error isolation
     */
    public Optional<EtaClientResponse> predictEta(EtaClientRequest req) {
        try {
            EtaClientResponse res = restClient.post()
                    .uri("/predict/eta")
                    .body(req)
                    .retrieve()
                    .body(EtaClientResponse.class);

            if (res != null && res.estimatedWaitMinutes() != null && res.estimatedWaitMinutes() >= 0) {
                return Optional.of(res);
            }
        } catch (Exception e) {
            log.warn("AI service ETA prediction unavailable: {}. Falling back to deterministic estimation.", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Call AI service for demand forecasting
     */
    public Optional<ForecastClientResponse> forecastDemand(ForecastClientRequest req) {
        try {
            ForecastClientResponse res = restClient.post()
                    .uri("/forecast/demand")
                    .body(req)
                    .retrieve()
                    .body(ForecastClientResponse.class);
            return Optional.ofNullable(res);
        } catch (Exception e) {
            log.warn("AI service demand forecast unavailable: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Call AI service for anomaly detection
     */
    public Optional<AnomalyClientResponse> detectAnomalies(AnomalyClientRequest req) {
        try {
            AnomalyClientResponse res = restClient.post()
                    .uri("/detect/anomalies")
                    .body(req)
                    .retrieve()
                    .body(AnomalyClientResponse.class);
            return Optional.ofNullable(res);
        } catch (Exception e) {
            log.warn("AI service anomaly detection unavailable: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Call AI service for operational recommendations
     */
    public Optional<RecommendationClientResponse> getRecommendations(RecommendationClientRequest req) {
        try {
            RecommendationClientResponse res = restClient.post()
                    .uri("/recommendations")
                    .body(req)
                    .retrieve()
                    .body(RecommendationClientResponse.class);
            return Optional.ofNullable(res);
        } catch (Exception e) {
            log.warn("AI service recommendations unavailable: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Call AI service for document verification with fallback
     */
    public Optional<DocumentVerifyClientResponse> verifyDocument(DocumentVerifyClientRequest req) {
        try {
            DocumentVerifyClientResponse res = restClient.post()
                    .uri("/verify/document")
                    .body(req)
                    .retrieve()
                    .body(DocumentVerifyClientResponse.class);
            return Optional.ofNullable(res);
        } catch (Exception e) {
            log.warn("AI service document verification unavailable: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
