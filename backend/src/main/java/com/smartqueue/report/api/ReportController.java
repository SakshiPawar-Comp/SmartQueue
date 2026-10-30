package com.smartqueue.report.api;

import com.smartqueue.analytics.api.dto.OperationalOverviewResponse;
import com.smartqueue.analytics.application.AnalyticsService;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.report.application.ReportService;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
public class ReportController {

    private final ReportService reportService;
    private final AnalyticsService analyticsService;

    public ReportController(ReportService reportService, AnalyticsService analyticsService) {
        this.reportService = reportService;
        this.analyticsService = analyticsService;
    }

    /** GET /api/reports/daily or /api/reports/daily/csv - Export Daily Report as CSV */
    @GetMapping(value = {"/daily", "/daily/csv"}, produces = "text/csv")
    public ResponseEntity<byte[]> exportDailyReportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId
    ) {
        byte[] csvData = reportService.generateDailyReportCsv(from, to, branchId, serviceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"daily-operational-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    /** GET /api/reports/queues or /api/reports/queues/csv - Export Queue Performance Report as CSV */
    @GetMapping(value = {"/queues", "/queues/csv"}, produces = "text/csv")
    public ResponseEntity<byte[]> exportQueueReportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId
    ) {
        byte[] csvData = reportService.generateQueueReportCsv(from, to, branchId, serviceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"queue-performance-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    /** GET /api/reports/services or /api/reports/services/csv - Export Service Performance Report as CSV */
    @GetMapping(value = {"/services", "/services/csv"}, produces = "text/csv")
    public ResponseEntity<byte[]> exportServiceReportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId
    ) {
        byte[] csvData = reportService.generateServiceReportCsv(from, to, branchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"service-performance-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    /** GET /api/reports/summary/csv - Export Executive Summary as CSV */
    @GetMapping(value = "/summary/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportSummaryReportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID queueId
    ) {
        byte[] csvData = reportService.generateSummaryReportCsv(from, to, branchId, serviceId, queueId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"operational-summary-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    /** GET /api/reports/summary - Get summary report JSON */
    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<OperationalOverviewResponse>> getSummaryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID queueId
    ) {
        OperationalOverviewResponse summary = analyticsService.getOverview(from, to, branchId, serviceId, queueId);
        return ResponseEntity.ok(ApiResponse.success(summary, MDC.get("correlationId")));
    }
}
