package com.smartqueue.ai.application;

import com.smartqueue.ai.api.dto.*;
import com.smartqueue.ai.infrastructure.AiClient;
import com.smartqueue.analytics.infrastructure.AnalyticsRepository;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.queue.application.QueueNotFoundException;
import com.smartqueue.queue.application.TokenNotFoundException;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Transactional(readOnly = true)
public class AiService {

    private final AiClient aiClient;
    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final ServiceRepository serviceRepository;
    private final CounterQueueAssignmentRepository counterQueueAssignmentRepository;
    private final AnalyticsRepository analyticsRepository;

    public AiService(
            AiClient aiClient,
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository,
            ServiceRepository serviceRepository,
            CounterQueueAssignmentRepository counterQueueAssignmentRepository,
            AnalyticsRepository analyticsRepository
    ) {
        this.aiClient = aiClient;
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.serviceRepository = serviceRepository;
        this.counterQueueAssignmentRepository = counterQueueAssignmentRepository;
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * AI-assisted ETA prediction for a customer token with deterministic fallback
     */
    public AiEtaResponse predictEta(UUID tokenId) {
        QueueToken token = queueTokenRepository.findById(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found: " + tokenId);
        }

        Queue queue = token.getQueue();
        int serviceTime = queue.getDefaultServiceTimeMinutes() != null && queue.getDefaultServiceTimeMinutes() > 0
                ? queue.getDefaultServiceTimeMinutes() : 5;

        // Terminal tokens have 0 wait time
        if (token.getStatus() == TokenStatus.COMPLETED || token.getStatus() == TokenStatus.CANCELLED || token.getStatus() == TokenStatus.SKIPPED) {
            return new AiEtaResponse(
                    0,
                    0,
                    "TERMINAL",
                    1.0,
                    Map.of("status", token.getStatus().name()),
                    "baseline-deterministic-v1"
            );
        }

        long tokensAhead = 0;
        int position = 1;

        if (token.getStatus() == TokenStatus.WAITING) {
            boolean isPriorityEnabled = Boolean.TRUE.equals(queue.getIsPriorityEnabled());
            tokensAhead = queueTokenRepository.countTokensAhead(
                    queue.getId(),
                    token.getId(),
                    token.getPriority(),
                    token.getCreatedAt(),
                    isPriorityEnabled
            );
            position = (int) tokensAhead + 1;
        } else if (token.getStatus() == TokenStatus.CALLED || token.getStatus() == TokenStatus.HOLD) {
            position = 1;
            tokensAhead = 0;
        }

        // Fast path for 0 tokens ahead
        if (tokensAhead == 0) {
            return new AiEtaResponse(
                    position,
                    0,
                    "DETERMINISTIC",
                    1.0,
                    Map.of("tokensAhead", 0, "defaultServiceTime", serviceTime),
                    "baseline-deterministic-v1"
            );
        }

        // Baseline deterministic fallback calculation
        int deterministicWait = (int) (tokensAhead * serviceTime);

        // Gather aggregate operational features (no PII)
        ZonedDateTime now = ZonedDateTime.now();
        var recentRecords = analyticsRepository.findTokenTimeRecords(
                queue.getOrganizationId(),
                queue.getBranchId(),
                queue.getServiceId(),
                queue.getId(),
                now.minusDays(7),
                now
        );

        Double rollingAvgService = recentRecords.stream()
                .map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream().boxed().findFirst().orElse(null);

        // Count assigned active counters
        long assignedCounters = counterQueueAssignmentRepository.findByQueueId(queue.getId()).size();
        int activeCounters = Math.max(1, (int) assignedCounters);

        int priorityRank = token.getPriority() != null ? token.getPriority().getRank() : 2;

        AiClient.EtaClientRequest clientReq = new AiClient.EtaClientRequest(
                queue.getId().toString(),
                token.getId().toString(),
                (int) tokensAhead,
                serviceTime,
                rollingAvgService != null ? AnalyticsRepository.round(rollingAvgService) : null,
                activeCounters,
                priorityRank,
                now.getHour(),
                now.getDayOfWeek().getValue()
        );

        Optional<AiClient.EtaClientResponse> aiRes = aiClient.predictEta(clientReq);

        if (aiRes.isPresent()) {
            AiClient.EtaClientResponse r = aiRes.get();
            return new AiEtaResponse(
                    position,
                    r.estimatedWaitMinutes(),
                    r.predictionType() != null ? r.predictionType() : "AI_PREDICTED",
                    r.confidenceScore() != null ? r.confidenceScore() : 0.85,
                    r.factors() != null ? r.factors() : Map.of(),
                    r.modelVersion() != null ? r.modelVersion() : "ai-v1.0"
            );
        }

        // Deterministic fallback
        return new AiEtaResponse(
                position,
                deterministicWait,
                "DETERMINISTIC",
                null,
                Map.of(
                        "tokensAhead", tokensAhead,
                        "defaultServiceTime", serviceTime,
                        "activeCounters", activeCounters,
                        "rollingAvgService", rollingAvgService != null ? AnalyticsRepository.round(rollingAvgService) : "N/A"
                ),
                "baseline-deterministic-v1"
        );
    }

    /**
     * AI-assisted hourly demand forecast
     */
    public AiForecastResponse forecastDemand(UUID queueId, UUID serviceId, int horizonHours) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        int horizon = Math.min(24, Math.max(1, horizonHours));

        if (queueId != null) {
            Queue q = queueRepository.findById(queueId);
            if (q == null || !q.getOrganizationId().equals(orgId)) {
                throw new QueueNotFoundException("Queue not found or access denied");
            }
        }

        if (serviceId != null) {
            Service s = serviceRepository.findById(serviceId);
            if (s == null || !s.getOrganization().getId().equals(orgId)) {
                throw new IllegalArgumentException("Service not found or access denied");
            }
        }

        ZonedDateTime now = ZonedDateTime.now();
        var records = analyticsRepository.findTokenTimeRecords(
                orgId, null, serviceId, queueId, now.minusDays(14), now
        );

        // Group by Date + Hour
        Map<String, Integer> hourlyMap = new HashMap<>();
        for (var r : records) {
            if (r.createdAt() != null) {
                String key = r.createdAt().toLocalDate().toString() + "_" + r.createdAt().getHour();
                hourlyMap.merge(key, 1, Integer::sum);
            }
        }

        List<AiClient.HourlyPoint> points = new ArrayList<>();
        for (Map.Entry<String, Integer> e : hourlyMap.entrySet()) {
            String[] parts = e.getKey().split("_");
            points.add(new AiClient.HourlyPoint(parts[0], Integer.parseInt(parts[1]), e.getValue()));
        }

        if (points.size() < 10) {
            return new AiForecastResponse(
                    "INSUFFICIENT_DATA",
                    Collections.emptyList(),
                    "Insufficient historical hourly volume data (minimum 10 data points required, available: " + points.size() + ")",
                    "ai-statistical-v1.0.0"
            );
        }

        AiClient.ForecastClientRequest req = new AiClient.ForecastClientRequest(
                queueId != null ? queueId.toString() : null,
                serviceId != null ? serviceId.toString() : null,
                horizon,
                points
        );

        Optional<AiClient.ForecastClientResponse> res = aiClient.forecastDemand(req);
        if (res.isPresent() && res.get().forecast() != null) {
            var f = res.get();
            List<AiForecastResponse.ForecastPointDto> list = f.forecast().stream()
                    .map(p -> new AiForecastResponse.ForecastPointDto(p.hourOffset(), p.predictedCount(), p.lowerBound(), p.upperBound()))
                    .toList();

            return new AiForecastResponse(
                    f.status(),
                    list,
                    f.message(),
                    f.modelVersion()
            );
        }

        // Statistical fallback if AI client fails
        double avgHourly = (double) records.size() / Math.max(1, points.size());
        List<AiForecastResponse.ForecastPointDto> fallbackPoints = new ArrayList<>();
        for (int h = 1; h <= horizon; h++) {
            int pred = (int) Math.round(avgHourly);
            fallbackPoints.add(new AiForecastResponse.ForecastPointDto(h, pred, Math.max(0, pred - 2), pred + 2));
        }

        return new AiForecastResponse(
                "FALLBACK",
                fallbackPoints,
                "Statistical average fallback forecast",
                "baseline-statistical-v1"
        );
    }

    /**
     * AI-assisted operational anomaly detection
     */
    public AiAnomalyResponse detectAnomalies(UUID branchId, UUID queueId) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        ZonedDateTime now = ZonedDateTime.now();

        // 30-day baseline vs 24h current window
        var baselineRecords = analyticsRepository.findTokenTimeRecords(orgId, branchId, null, queueId, now.minusDays(30), now.minusDays(1));
        var currentRecords = analyticsRepository.findTokenTimeRecords(orgId, branchId, null, queueId, now.minusDays(1), now);

        List<AiClient.MetricCheckClient> checks = new ArrayList<>();

        // 1. Wait Time Metric
        List<Double> baseWaits = baselineRecords.stream().map(AnalyticsRepository.TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();
        List<Double> currWaits = currentRecords.stream().map(AnalyticsRepository.TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();

        if (!baseWaits.isEmpty() && !currWaits.isEmpty()) {
            double baseMean = baseWaits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double baseStd = calculateStdDev(baseWaits, baseMean);
            double currMean = currWaits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            checks.add(new AiClient.MetricCheckClient(
                    "WAIT_TIME",
                    currMean,
                    baseMean,
                    baseStd,
                    now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ));
        }

        // 2. Service Time Metric
        List<Double> baseServ = baselineRecords.stream().map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();
        List<Double> currServ = currentRecords.stream().map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();

        if (!baseServ.isEmpty() && !currServ.isEmpty()) {
            double baseMean = baseServ.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double baseStd = calculateStdDev(baseServ, baseMean);
            double currMean = currServ.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            checks.add(new AiClient.MetricCheckClient(
                    "SERVICE_TIME",
                    currMean,
                    baseMean,
                    baseStd,
                    now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ));
        }

        // 3. Cancellation Rate Metric
        if (!baselineRecords.isEmpty() && !currentRecords.isEmpty()) {
            double baseCancelRate = ((double) baselineRecords.stream().filter(r -> r.status() == TokenStatus.CANCELLED).count() / baselineRecords.size()) * 100.0;
            double currCancelRate = ((double) currentRecords.stream().filter(r -> r.status() == TokenStatus.CANCELLED).count() / currentRecords.size()) * 100.0;

            checks.add(new AiClient.MetricCheckClient(
                    "CANCELLATION_RATE",
                    currCancelRate,
                    baseCancelRate,
                    Math.max(5.0, baseCancelRate * 0.4),
                    now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ));
        }

        String queueName = null;
        if (queueId != null) {
            Queue q = queueRepository.findById(queueId);
            if (q != null) queueName = q.getName();
        }

        AiClient.AnomalyClientRequest req = new AiClient.AnomalyClientRequest(
                queueId != null ? queueId.toString() : null,
                queueName,
                checks
        );

        Optional<AiClient.AnomalyClientResponse> res = aiClient.detectAnomalies(req);
        if (res.isPresent() && res.get().anomalies() != null) {
            List<AiAnomalyResponse.AnomalyItemDto> list = res.get().anomalies().stream()
                    .map(a -> new AiAnomalyResponse.AnomalyItemDto(
                            a.type(),
                            a.severity(),
                            a.queueId() != null ? UUID.fromString(a.queueId()) : null,
                            a.queueName(),
                            a.observedValue(),
                            a.baselineValue(),
                            a.deviationFactor(),
                            a.message(),
                            a.timestamp()
                    ))
                    .toList();

            return new AiAnomalyResponse(list, res.get().modelVersion());
        }

        // Local statistical fallback
        List<AiAnomalyResponse.AnomalyItemDto> fallbackList = new ArrayList<>();
        for (var c : checks) {
            double diff = c.observedValue() - c.baselineMean();
            double z = diff / Math.max(0.1, c.baselineStdDev());
            if (z >= 1.5) {
                String sev = z >= 4.0 ? "CRITICAL" : (z >= 3.0 ? "HIGH" : (z >= 2.0 ? "MEDIUM" : "LOW"));
                fallbackList.add(new AiAnomalyResponse.AnomalyItemDto(
                        c.metricName(),
                        sev,
                        queueId,
                        queueName,
                        AnalyticsRepository.round(c.observedValue()),
                        AnalyticsRepository.round(c.baselineMean()),
                        AnalyticsRepository.round(z),
                        "Observed " + c.metricName() + " (" + AnalyticsRepository.round(c.observedValue()) + ") exceeds baseline (" + AnalyticsRepository.round(c.baselineMean()) + ") by " + AnalyticsRepository.round(z) + " std deviations.",
                        c.timestamp()
                ));
            }
        }

        return new AiAnomalyResponse(fallbackList, "baseline-statistical-v1");
    }

    /**
     * AI-assisted operational recommendations
     */
    public AiRecommendationResponse getRecommendations(UUID branchId, UUID queueId) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        ZonedDateTime now = ZonedDateTime.now();

        var records = analyticsRepository.findTokenTimeRecords(orgId, branchId, null, queueId, now.minusDays(1), now);
        long waiting = records.stream().filter(r -> r.status() == TokenStatus.WAITING).count();

        List<Double> waits = records.stream().map(AnalyticsRepository.TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();
        double avgWait = waits.isEmpty() ? 0.0 : waits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        long cancelled = records.stream().filter(r -> r.status() == TokenStatus.CANCELLED).count();
        double cancelRate = records.isEmpty() ? 0.0 : ((double) cancelled / records.size()) * 100.0;

        String queueName = "All Queues";
        long counterCount = 1;
        if (queueId != null) {
            Queue q = queueRepository.findById(queueId);
            if (q != null) {
                queueName = q.getName();
                counterCount = Math.max(1, counterQueueAssignmentRepository.findByQueueId(queueId).size());
            }
        }

        AiClient.RecommendationClientRequest req = new AiClient.RecommendationClientRequest(
                queueId != null ? queueId.toString() : null,
                queueName,
                (int) waiting,
                AnalyticsRepository.round(avgWait),
                (int) counterCount,
                AnalyticsRepository.round(cancelRate),
                0.85
        );

        Optional<AiClient.RecommendationClientResponse> res = aiClient.getRecommendations(req);
        if (res.isPresent() && res.get().recommendations() != null) {
            List<AiRecommendationResponse.RecommendationDto> list = res.get().recommendations().stream()
                    .map(r -> new AiRecommendationResponse.RecommendationDto(
                            r.id(),
                            r.category(),
                            r.priority(),
                            r.title(),
                            r.description(),
                            r.evidence(),
                            r.targetQueueId() != null ? UUID.fromString(r.targetQueueId()) : null
                    ))
                    .toList();

            return new AiRecommendationResponse(list, res.get().modelVersion());
        }

        // Local fallback recommendations
        List<AiRecommendationResponse.RecommendationDto> fallbackList = new ArrayList<>();
        if (avgWait > 20.0 && counterCount <= 1) {
            fallbackList.add(new AiRecommendationResponse.RecommendationDto(
                    "rec-staff-1",
                    "STAFFING",
                    "HIGH",
                    "Open Additional Counter for " + queueName,
                    "Current average wait time is " + AnalyticsRepository.round(avgWait) + " mins with only " + counterCount + " counter active. Recommend opening an extra counter.",
                    "Average wait time = " + AnalyticsRepository.round(avgWait) + " mins (threshold > 20 mins), Active counters = " + counterCount,
                    queueId
            ));
        }

        return new AiRecommendationResponse(fallbackList, "baseline-statistical-v1");
    }

    private double calculateStdDev(List<Double> values, double mean) {
        if (values.size() <= 1) return 1.0;
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / (values.size() - 1));
    }
}
