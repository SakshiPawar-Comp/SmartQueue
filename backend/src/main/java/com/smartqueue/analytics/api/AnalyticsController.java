package com.smartqueue.analytics.api;

import com.smartqueue.analytics.api.dto.*;
import com.smartqueue.analytics.application.AnalyticsService;
import com.smartqueue.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN', 'STAFF')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** GET /api/analytics/overview - High-level operational KPI overview */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<OperationalOverviewResponse>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID queueId
    ) {
        var overview = analyticsService.getOverview(from, to, branchId, serviceId, queueId);
        return ResponseEntity.ok(ApiResponse.success(overview, MDC.get("correlationId")));
    }

    /** GET /api/analytics/queues - Queue-level performance metrics */
    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<List<QueueAnalyticsResponse>>> getQueuesAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId
    ) {
        var queues = analyticsService.getQueuesAnalytics(from, to, branchId, serviceId);
        return ResponseEntity.ok(ApiResponse.success(queues, MDC.get("correlationId")));
    }

    /** GET /api/analytics/services - Service-level volume & wait comparison */
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceAnalyticsResponse>>> getServicesAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId
    ) {
        var services = analyticsService.getServicesAnalytics(from, to, branchId);
        return ResponseEntity.ok(ApiResponse.success(services, MDC.get("correlationId")));
    }

    /** GET /api/analytics/counters - Counter throughput and service metrics */
    @GetMapping("/counters")
    public ResponseEntity<ApiResponse<List<CounterAnalyticsResponse>>> getCountersAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId
    ) {
        var counters = analyticsService.getCountersAnalytics(from, to, branchId);
        return ResponseEntity.ok(ApiResponse.success(counters, MDC.get("correlationId")));
    }

    /** GET /api/analytics/staff - Staff operational performance (Admin only) */
    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffAnalyticsResponse>>> getStaffAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId
    ) {
        var staff = analyticsService.getStaffAnalytics(from, to, branchId);
        return ResponseEntity.ok(ApiResponse.success(staff, MDC.get("correlationId")));
    }

    /** GET /api/analytics/peak-hours - Hourly customer volume distribution */
    @GetMapping("/peak-hours")
    public ResponseEntity<ApiResponse<List<PeakHourResponse>>> getPeakHours(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID queueId
    ) {
        var peakHours = analyticsService.getPeakHours(from, to, branchId, queueId);
        return ResponseEntity.ok(ApiResponse.success(peakHours, MDC.get("correlationId")));
    }

    /** GET /api/analytics/wait-times - Wait time percentiles and distribution */
    @GetMapping("/wait-times")
    public ResponseEntity<ApiResponse<WaitTimeDistributionResponse>> getWaitTimeDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID queueId
    ) {
        var waitTimes = analyticsService.getWaitTimeDistribution(from, to, queueId);
        return ResponseEntity.ok(ApiResponse.success(waitTimes, MDC.get("correlationId")));
    }
}
