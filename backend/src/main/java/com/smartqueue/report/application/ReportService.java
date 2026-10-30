package com.smartqueue.report.application;

import com.smartqueue.analytics.api.dto.OperationalOverviewResponse;
import com.smartqueue.analytics.api.dto.QueueAnalyticsResponse;
import com.smartqueue.analytics.api.dto.ServiceAnalyticsResponse;
import com.smartqueue.analytics.application.AnalyticsService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
public class ReportService {

    private final AnalyticsService analyticsService;

    public ReportService(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    private String escapeCsv(Object value) {
        if (value == null) return "";
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    /**
     * Generate Daily Operational Report CSV
     */
    public byte[] generateDailyReportCsv(ZonedDateTime from, ZonedDateTime to, UUID branchId, UUID serviceId) {
        AnalyticsService.RangeBounds bounds = analyticsService.resolveBounds(from, to);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Total Tokens,Completed,Cancelled,Skipped,Avg Wait (mins),Median Wait (mins),Avg Service (mins),Completion Rate (%),Cancellation Rate (%)\n");

        // Iterate day by day across the range
        ZonedDateTime cursor = bounds.from().toLocalDate().atStartOfDay(bounds.from().getZone());
        ZonedDateTime end = bounds.to();

        while (!cursor.isAfter(end)) {
            ZonedDateTime dayStart = cursor;
            ZonedDateTime dayEnd = cursor.plusDays(1).minusNanos(1);
            if (dayEnd.isAfter(end)) dayEnd = end;

            OperationalOverviewResponse dayOverview = analyticsService.getOverview(dayStart, dayEnd, branchId, serviceId, null);
            String dateStr = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);

            csv.append(escapeCsv(dateStr)).append(",")
                    .append(dayOverview.totalTokens()).append(",")
                    .append(dayOverview.completedTokens()).append(",")
                    .append(dayOverview.cancelledTokens()).append(",")
                    .append(dayOverview.skippedTokens()).append(",")
                    .append(dayOverview.avgWaitMinutes()).append(",")
                    .append(dayOverview.medianWaitMinutes()).append(",")
                    .append(dayOverview.avgServiceMinutes()).append(",")
                    .append(dayOverview.completionRate()).append(",")
                    .append(dayOverview.cancellationRate()).append("\n");

            cursor = cursor.plusDays(1);
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Queue Performance Report CSV
     */
    public byte[] generateQueueReportCsv(ZonedDateTime from, ZonedDateTime to, UUID branchId, UUID serviceId) {
        List<QueueAnalyticsResponse> queues = analyticsService.getQueuesAnalytics(from, to, branchId, serviceId);
        StringBuilder csv = new StringBuilder();
        csv.append("Queue Name,Token Prefix,Branch,Service,Total Tokens,Completed,Cancelled,Skipped,Current Waiting,Avg Wait (mins),Avg Service (mins),Peak Hour,Throughput/Hr\n");

        for (QueueAnalyticsResponse q : queues) {
            csv.append(escapeCsv(q.queueName())).append(",")
                    .append(escapeCsv(q.tokenPrefix())).append(",")
                    .append(escapeCsv(q.branchName())).append(",")
                    .append(escapeCsv(q.serviceName())).append(",")
                    .append(q.totalTokens()).append(",")
                    .append(q.completedTokens()).append(",")
                    .append(q.cancelledTokens()).append(",")
                    .append(q.skippedTokens()).append(",")
                    .append(q.currentWaiting()).append(",")
                    .append(q.avgWaitMinutes()).append(",")
                    .append(q.avgServiceMinutes()).append(",")
                    .append(q.peakHour()).append(":00,")
                    .append(q.throughputPerHour()).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Service Performance Report CSV
     */
    public byte[] generateServiceReportCsv(ZonedDateTime from, ZonedDateTime to, UUID branchId) {
        List<ServiceAnalyticsResponse> services = analyticsService.getServicesAnalytics(from, to, branchId);
        StringBuilder csv = new StringBuilder();
        csv.append("Service Name,Branch,Total Tokens,Completed,Cancelled,Skipped,Avg Wait (mins),Avg Service (mins),Completion Rate (%)\n");

        for (ServiceAnalyticsResponse s : services) {
            csv.append(escapeCsv(s.serviceName())).append(",")
                    .append(escapeCsv(s.branchName())).append(",")
                    .append(s.totalTokens()).append(",")
                    .append(s.completedTokens()).append(",")
                    .append(s.cancelledTokens()).append(",")
                    .append(s.skippedTokens()).append(",")
                    .append(s.avgWaitMinutes()).append(",")
                    .append(s.avgServiceMinutes()).append(",")
                    .append(s.completionRate()).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Operational Summary Report CSV
     */
    public byte[] generateSummaryReportCsv(ZonedDateTime from, ZonedDateTime to, UUID branchId, UUID serviceId, UUID queueId) {
        OperationalOverviewResponse overview = analyticsService.getOverview(from, to, branchId, serviceId, queueId);
        AnalyticsService.RangeBounds bounds = analyticsService.resolveBounds(from, to);

        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Value\n");
        csv.append("Report From,").append(escapeCsv(bounds.from().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).append("\n");
        csv.append("Report To,").append(escapeCsv(bounds.to().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).append("\n");
        csv.append("Total Tokens,").append(overview.totalTokens()).append("\n");
        csv.append("Completed Tokens,").append(overview.completedTokens()).append("\n");
        csv.append("Cancelled Tokens,").append(overview.cancelledTokens()).append("\n");
        csv.append("Skipped Tokens,").append(overview.skippedTokens()).append("\n");
        csv.append("Currently Waiting,").append(overview.waitingTokens()).append("\n");
        csv.append("Currently Serving,").append(overview.servingTokens()).append("\n");
        csv.append("Average Wait Time (mins),").append(overview.avgWaitMinutes()).append("\n");
        csv.append("Median Wait Time (mins),").append(overview.medianWaitMinutes()).append("\n");
        csv.append("Max Wait Time (mins),").append(overview.maxWaitMinutes()).append("\n");
        csv.append("Average Service Time (mins),").append(overview.avgServiceMinutes()).append("\n");
        csv.append("Median Service Time (mins),").append(overview.medianServiceMinutes()).append("\n");
        csv.append("Max Service Time (mins),").append(overview.maxServiceMinutes()).append("\n");
        csv.append("Throughput Per Hour,").append(overview.throughputPerHour()).append("\n");
        csv.append("Completion Rate (%),").append(overview.completionRate()).append("\n");
        csv.append("Cancellation Rate (%),").append(overview.cancellationRate()).append("\n");
        csv.append("Skip Rate (%),").append(overview.skipRate()).append("\n");

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
