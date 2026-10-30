package com.smartqueue.analytics.application;

import com.smartqueue.analytics.api.dto.*;
import com.smartqueue.analytics.infrastructure.AnalyticsRepository;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

@Component
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final QueueRepository queueRepository;
    private final CounterRepository counterRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            AnalyticsRepository analyticsRepository,
            BranchRepository branchRepository,
            ServiceRepository serviceRepository,
            QueueRepository queueRepository,
            CounterRepository counterRepository,
            UserRepository userRepository
    ) {
        this.analyticsRepository = analyticsRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
        this.queueRepository = queueRepository;
        this.counterRepository = counterRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resolve and validate date range boundaries (defaulting to last 7 days if omitted)
     */
    public RangeBounds resolveBounds(ZonedDateTime from, ZonedDateTime to) {
        ZonedDateTime resolvedTo = to != null ? to : ZonedDateTime.now();
        ZonedDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(7);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("'from' timestamp cannot be after 'to' timestamp");
        }
        return new RangeBounds(resolvedFrom, resolvedTo);
    }

    public record RangeBounds(ZonedDateTime from, ZonedDateTime to) {}

    /**
     * Multi-level resource ownership validation
     */
    public void validateResourceScope(UUID orgId, UUID branchId, UUID serviceId, UUID queueId) {
        if (branchId != null) {
            Branch b = branchRepository.findById(branchId)
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));
            if (!b.getOrganization().getId().equals(orgId)) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
        }

        if (serviceId != null) {
            Service s = serviceRepository.findById(serviceId);
            if (s == null) {
                throw new IllegalArgumentException("Service not found: " + serviceId);
            }
            if (!s.getOrganization().getId().equals(orgId)) {
                throw new TenantViolationException("Service does not belong to your organization");
            }
        }

        if (queueId != null) {
            Queue q = queueRepository.findById(queueId);
            if (q == null) {
                throw new IllegalArgumentException("Queue not found: " + queueId);
            }
            if (!q.getOrganizationId().equals(orgId)) {
                throw new TenantViolationException("Queue does not belong to your organization");
            }
        }
    }

    /**
     * Get operational overview metrics
     */
    public OperationalOverviewResponse getOverview(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID branchId,
            UUID serviceId,
            UUID queueId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, branchId, serviceId, queueId);
        RangeBounds bounds = resolveBounds(from, to);

        return analyticsRepository.computeOverview(orgId, branchId, serviceId, queueId, bounds.from(), bounds.to());
    }

    /**
     * Get queue-level performance analytics
     */
    public List<QueueAnalyticsResponse> getQueuesAnalytics(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID branchId,
            UUID serviceId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, branchId, serviceId, null);
        RangeBounds bounds = resolveBounds(from, to);

        List<Queue> queues = queueRepository.findByOrganizationId(orgId);
        if (branchId != null) {
            queues = queues.stream().filter(q -> branchId.equals(q.getBranchId())).toList();
        }
        if (serviceId != null) {
            queues = queues.stream().filter(q -> serviceId.equals(q.getServiceId())).toList();
        }

        List<QueueAnalyticsResponse> results = new ArrayList<>();
        for (Queue q : queues) {
            String branchName = "-";
            if (q.getBranchId() != null) {
                branchName = branchRepository.findById(q.getBranchId()).map(Branch::getName).orElse("-");
            }
            String serviceName = "-";
            if (q.getServiceId() != null) {
                Service s = serviceRepository.findById(q.getServiceId());
                if (s != null) serviceName = s.getName();
            }

            var records = analyticsRepository.findTokenTimeRecords(orgId, null, null, q.getId(), bounds.from(), bounds.to());
            long total = records.size();
            long completed = records.stream().filter(r -> r.status() == TokenStatus.COMPLETED).count();
            long cancelled = records.stream().filter(r -> r.status() == TokenStatus.CANCELLED).count();
            long skipped = records.stream().filter(r -> r.status() == TokenStatus.SKIPPED).count();
            long currentWaiting = records.stream().filter(r -> r.status() == TokenStatus.WAITING).count();

            List<Double> waits = records.stream().map(AnalyticsRepository.TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();
            List<Double> services = records.stream().map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();

            double avgW = waits.isEmpty() ? 0.0 : waits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgS = services.isEmpty() ? 0.0 : services.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            // Find peak hour
            Map<Integer, Long> hourCounts = new HashMap<>();
            for (var r : records) {
                if (r.createdAt() != null) {
                    hourCounts.merge(r.createdAt().getHour(), 1L, Long::sum);
                }
            }
            int peakHour = hourCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(12);

            double hours = Math.max(1.0, (double) Duration.between(bounds.from(), bounds.to()).toSeconds() / 3600.0);
            double throughput = (double) completed / hours;

            results.add(new QueueAnalyticsResponse(
                    q.getId(),
                    q.getName(),
                    q.getTokenPrefix(),
                    branchName,
                    serviceName,
                    total,
                    completed,
                    cancelled,
                    skipped,
                    currentWaiting,
                    AnalyticsRepository.round(avgW),
                    AnalyticsRepository.round(avgS),
                    peakHour,
                    AnalyticsRepository.round(throughput)
            ));
        }

        return results;
    }

    /**
     * Get service-level analytics
     */
    public List<ServiceAnalyticsResponse> getServicesAnalytics(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID branchId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, branchId, null, null);
        RangeBounds bounds = resolveBounds(from, to);

        List<Service> services = serviceRepository.findByOrganizationId(orgId);
        if (branchId != null) {
            services = services.stream().filter(s -> s.getBranch() != null && branchId.equals(s.getBranch().getId())).toList();
        }

        List<ServiceAnalyticsResponse> results = new ArrayList<>();
        for (Service s : services) {
            String branchName = s.getBranch() != null ? s.getBranch().getName() : "-";
            var records = analyticsRepository.findTokenTimeRecords(orgId, null, s.getId(), null, bounds.from(), bounds.to());

            long total = records.size();
            long completed = records.stream().filter(r -> r.status() == TokenStatus.COMPLETED).count();
            long cancelled = records.stream().filter(r -> r.status() == TokenStatus.CANCELLED).count();
            long skipped = records.stream().filter(r -> r.status() == TokenStatus.SKIPPED).count();

            List<Double> waits = records.stream().map(AnalyticsRepository.TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();
            List<Double> servs = records.stream().map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();

            double avgW = waits.isEmpty() ? 0.0 : waits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgS = servs.isEmpty() ? 0.0 : servs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double compRate = total == 0 ? 0.0 : ((double) completed / total) * 100.0;

            results.add(new ServiceAnalyticsResponse(
                    s.getId(),
                    s.getName(),
                    branchName,
                    total,
                    completed,
                    cancelled,
                    skipped,
                    AnalyticsRepository.round(avgW),
                    AnalyticsRepository.round(avgS),
                    AnalyticsRepository.round(compRate)
            ));
        }
        return results;
    }

    /**
     * Get counter performance analytics
     */
    public List<CounterAnalyticsResponse> getCountersAnalytics(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID branchId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, branchId, null, null);
        RangeBounds bounds = resolveBounds(from, to);

        List<Counter> counters = counterRepository.findByOrganizationId(orgId);
        if (branchId != null) {
            counters = counters.stream().filter(c -> c.getBranch() != null && branchId.equals(c.getBranch().getId())).toList();
        }

        var allRecords = analyticsRepository.findTokenTimeRecords(orgId, branchId, null, null, bounds.from(), bounds.to());

        Map<UUID, List<AnalyticsRepository.TokenTimeRecord>> counterMap = new HashMap<>();
        for (var r : allRecords) {
            if (r.servedByCounterId() != null) {
                counterMap.computeIfAbsent(r.servedByCounterId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<CounterAnalyticsResponse> results = new ArrayList<>();
        for (Counter c : counters) {
            String branchName = c.getBranch() != null ? c.getBranch().getName() : "-";
            List<AnalyticsRepository.TokenTimeRecord> recs = counterMap.getOrDefault(c.getId(), Collections.emptyList());

            long served = recs.stream().filter(r -> r.status() == TokenStatus.COMPLETED).count();
            List<Double> servs = recs.stream().map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();
            List<Double> waits = recs.stream().map(AnalyticsRepository.TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();

            double avgS = servs.isEmpty() ? 0.0 : servs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgW = waits.isEmpty() ? 0.0 : waits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            double hours = Math.max(1.0, (double) Duration.between(bounds.from(), bounds.to()).toSeconds() / 3600.0);
            double throughput = (double) served / hours;

            results.add(new CounterAnalyticsResponse(
                    c.getId(),
                    c.getName(),
                    c.getCode(),
                    branchName,
                    served,
                    AnalyticsRepository.round(avgS),
                    AnalyticsRepository.round(avgW),
                    AnalyticsRepository.round(throughput)
            ));
        }

        return results;
    }

    /**
     * Get staff performance analytics
     */
    public List<StaffAnalyticsResponse> getStaffAnalytics(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID branchId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, branchId, null, null);
        RangeBounds bounds = resolveBounds(from, to);

        var allRecords = analyticsRepository.findTokenTimeRecords(orgId, branchId, null, null, bounds.from(), bounds.to());

        Map<UUID, List<AnalyticsRepository.TokenTimeRecord>> userMap = new HashMap<>();
        for (var r : allRecords) {
            if (r.servedByUserId() != null) {
                userMap.computeIfAbsent(r.servedByUserId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<StaffAnalyticsResponse> results = new ArrayList<>();
        for (Map.Entry<UUID, List<AnalyticsRepository.TokenTimeRecord>> entry : userMap.entrySet()) {
            UUID userId = entry.getKey();
            List<AnalyticsRepository.TokenTimeRecord> recs = entry.getValue();

            User u = userRepository.findById(userId).orElse(null);
            String name = u != null ? u.getFullName() : "Staff Member";
            String email = u != null ? u.getEmail() : "staff@smartqueue.internal";

            long completed = recs.stream().filter(r -> r.status() == TokenStatus.COMPLETED).count();
            long skipped = recs.stream().filter(r -> r.status() == TokenStatus.SKIPPED).count();
            long totalServed = completed + skipped;

            List<Double> servs = recs.stream().map(AnalyticsRepository.TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();
            double avgS = servs.isEmpty() ? 0.0 : servs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            results.add(new StaffAnalyticsResponse(
                    userId,
                    name,
                    email,
                    totalServed,
                    completed,
                    skipped,
                    AnalyticsRepository.round(avgS)
            ));
        }

        return results;
    }

    /**
     * Get peak hour volume and wait trends
     */
    public List<PeakHourResponse> getPeakHours(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID branchId,
            UUID queueId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, branchId, null, queueId);
        RangeBounds bounds = resolveBounds(from, to);

        return analyticsRepository.computePeakHours(orgId, branchId, queueId, bounds.from(), bounds.to());
    }

    /**
     * Get wait time percentiles and distribution
     */
    public WaitTimeDistributionResponse getWaitTimeDistribution(
            ZonedDateTime from,
            ZonedDateTime to,
            UUID queueId
    ) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        validateResourceScope(orgId, null, null, queueId);
        RangeBounds bounds = resolveBounds(from, to);

        return analyticsRepository.computeWaitTimeDistribution(orgId, queueId, bounds.from(), bounds.to());
    }
}
