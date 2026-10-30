package com.smartqueue.analytics.infrastructure;

import com.smartqueue.analytics.api.dto.*;
import com.smartqueue.queue.domain.TokenPriority;
import com.smartqueue.queue.domain.TokenStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * DTO for raw token timestamp data fetched without entity overhead
     */
    public record TokenTimeRecord(
            UUID tokenId,
            UUID queueId,
            TokenStatus status,
            TokenPriority priority,
            UUID servedByCounterId,
            UUID servedByUserId,
            ZonedDateTime createdAt,
            ZonedDateTime calledAt,
            ZonedDateTime completedAt,
            ZonedDateTime cancelledAt,
            ZonedDateTime skippedAt
    ) {
        public Double getWaitMinutes() {
            if (calledAt != null && createdAt != null) {
                return (double) Duration.between(createdAt, calledAt).toSeconds() / 60.0;
            }
            if (completedAt != null && createdAt != null) {
                return (double) Duration.between(createdAt, completedAt).toSeconds() / 60.0;
            }
            return null;
        }

        public Double getServiceMinutes() {
            if (completedAt != null && calledAt != null) {
                long secs = Duration.between(calledAt, completedAt).toSeconds();
                return Math.max(0.0, (double) secs / 60.0);
            }
            return null;
        }
    }

    private UUID parseUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID) return (UUID) obj;
        if (obj instanceof String) return UUID.fromString((String) obj);
        if (obj instanceof byte[]) return java.util.UUID.nameUUIDFromBytes((byte[]) obj);
        throw new IllegalArgumentException("Cannot cast to UUID: " + obj.getClass());
    }

    private ZonedDateTime parseDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ZonedDateTime) return (ZonedDateTime) obj;
        if (obj instanceof java.time.OffsetDateTime) return ((java.time.OffsetDateTime) obj).toZonedDateTime();
        if (obj instanceof java.time.LocalDateTime) return ((java.time.LocalDateTime) obj).atZone(java.time.ZoneId.systemDefault());
        if (obj instanceof java.sql.Timestamp) return ((java.sql.Timestamp) obj).toInstant().atZone(java.time.ZoneId.systemDefault());
        if (obj instanceof java.time.Instant) return ((java.time.Instant) obj).atZone(java.time.ZoneId.systemDefault());
        if (obj instanceof String) return ZonedDateTime.parse((String) obj);
        throw new IllegalArgumentException("Cannot cast to ZonedDateTime: " + obj.getClass());
    }

    public List<TokenTimeRecord> findTokenTimeRecords(
            UUID organizationId,
            UUID branchId,
            UUID serviceId,
            UUID queueId,
            ZonedDateTime from,
            ZonedDateTime to
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT t.id, q.id, t.status, t.priority, t.servedByCounterId, t.servedByUserId, " +
                "t.createdAt, t.calledAt, t.completedAt, t.cancelledAt, t.skippedAt " +
                "FROM QueueToken t JOIN t.queue q " +
                "WHERE q.organizationId = :orgId " +
                "AND t.createdAt >= :from AND t.createdAt <= :to"
        );

        if (branchId != null) {
            jpql.append(" AND q.branchId = :branchId");
        }
        if (serviceId != null) {
            jpql.append(" AND q.serviceId = :serviceId");
        }
        if (queueId != null) {
            jpql.append(" AND q.id = :queueId");
        }

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        query.setParameter("orgId", organizationId);
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (branchId != null) query.setParameter("branchId", branchId);
        if (serviceId != null) query.setParameter("serviceId", serviceId);
        if (queueId != null) query.setParameter("queueId", queueId);

        List<Object[]> rows = query.getResultList();
        List<TokenTimeRecord> records = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            records.add(new TokenTimeRecord(
                    parseUuid(r[0]),
                    parseUuid(r[1]),
                    r[2] instanceof String ? TokenStatus.valueOf((String) r[2]) : (TokenStatus) r[2],
                    r[3] instanceof String ? TokenPriority.valueOf((String) r[3]) : (TokenPriority) r[3],
                    parseUuid(r[4]),
                    parseUuid(r[5]),
                    parseDateTime(r[6]),
                    parseDateTime(r[7]),
                    parseDateTime(r[8]),
                    parseDateTime(r[9]),
                    parseDateTime(r[10])
            ));
        }
        return records;
    }

    /**
     * Compute operational overview from token records
     */
    public OperationalOverviewResponse computeOverview(
            UUID organizationId,
            UUID branchId,
            UUID serviceId,
            UUID queueId,
            ZonedDateTime from,
            ZonedDateTime to
    ) {
        List<TokenTimeRecord> records = findTokenTimeRecords(organizationId, branchId, serviceId, queueId, from, to);

        long total = records.size();
        long completed = records.stream().filter(r -> r.status() == TokenStatus.COMPLETED).count();
        long cancelled = records.stream().filter(r -> r.status() == TokenStatus.CANCELLED).count();
        long skipped = records.stream().filter(r -> r.status() == TokenStatus.SKIPPED).count();
        long waiting = records.stream().filter(r -> r.status() == TokenStatus.WAITING).count();
        long serving = records.stream().filter(r -> r.status() == TokenStatus.CALLED).count();

        List<Double> waitTimes = records.stream()
                .map(TokenTimeRecord::getWaitMinutes)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        List<Double> serviceTimes = records.stream()
                .map(TokenTimeRecord::getServiceMinutes)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        double avgWait = waitTimes.isEmpty() ? 0.0 : waitTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double medianWait = calculateMedian(waitTimes);
        double maxWait = waitTimes.isEmpty() ? 0.0 : waitTimes.get(waitTimes.size() - 1);

        double avgService = serviceTimes.isEmpty() ? 0.0 : serviceTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double medianService = calculateMedian(serviceTimes);
        double maxService = serviceTimes.isEmpty() ? 0.0 : serviceTimes.get(serviceTimes.size() - 1);

        double hours = Math.max(1.0, (double) Duration.between(from, to).toSeconds() / 3600.0);
        double throughput = (double) completed / hours;

        double completionRate = total == 0 ? 0.0 : ((double) completed / total) * 100.0;
        double cancellationRate = total == 0 ? 0.0 : ((double) cancelled / total) * 100.0;
        double skipRate = total == 0 ? 0.0 : ((double) skipped / total) * 100.0;

        return new OperationalOverviewResponse(
                total,
                completed,
                cancelled,
                skipped,
                waiting,
                serving,
                round(avgWait),
                round(medianWait),
                round(maxWait),
                round(avgService),
                round(medianService),
                round(maxService),
                round(throughput),
                round(completionRate),
                round(cancellationRate),
                round(skipRate)
        );
    }

    /**
     * Compute peak hours distribution (0 to 23)
     */
    public List<PeakHourResponse> computePeakHours(
            UUID organizationId,
            UUID branchId,
            UUID queueId,
            ZonedDateTime from,
            ZonedDateTime to
    ) {
        List<TokenTimeRecord> records = findTokenTimeRecords(organizationId, branchId, null, queueId, from, to);

        Map<Integer, List<TokenTimeRecord>> hourGroups = new HashMap<>();
        for (int h = 0; h < 24; h++) {
            hourGroups.put(h, new ArrayList<>());
        }

        for (TokenTimeRecord r : records) {
            if (r.createdAt() != null) {
                int h = r.createdAt().getHour();
                hourGroups.computeIfAbsent(h, k -> new ArrayList<>()).add(r);
            }
        }

        List<PeakHourResponse> list = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            List<TokenTimeRecord> recs = hourGroups.get(h);
            long count = recs.size();
            long comp = recs.stream().filter(r -> r.status() == TokenStatus.COMPLETED).count();

            List<Double> waits = recs.stream().map(TokenTimeRecord::getWaitMinutes).filter(Objects::nonNull).toList();
            List<Double> services = recs.stream().map(TokenTimeRecord::getServiceMinutes).filter(Objects::nonNull).toList();

            double avgW = waits.isEmpty() ? 0.0 : waits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgS = services.isEmpty() ? 0.0 : services.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            list.add(new PeakHourResponse(h, count, comp, round(avgW), round(avgS)));
        }

        return list;
    }

    /**
     * Compute wait time distribution percentiles (p50, p75, p90, p95) and by priority
     */
    public WaitTimeDistributionResponse computeWaitTimeDistribution(
            UUID organizationId,
            UUID queueId,
            ZonedDateTime from,
            ZonedDateTime to
    ) {
        List<TokenTimeRecord> records = findTokenTimeRecords(organizationId, null, null, queueId, from, to);

        List<Double> allWaits = records.stream()
                .map(TokenTimeRecord::getWaitMinutes)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        double p50 = calculatePercentile(allWaits, 0.50);
        double p75 = calculatePercentile(allWaits, 0.75);
        double p90 = calculatePercentile(allWaits, 0.90);
        double p95 = calculatePercentile(allWaits, 0.95);
        double avgWait = allWaits.isEmpty() ? 0.0 : allWaits.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxWait = allWaits.isEmpty() ? 0.0 : allWaits.get(allWaits.size() - 1);

        double normalAvg = records.stream()
                .filter(r -> r.priority() == TokenPriority.NORMAL)
                .map(TokenTimeRecord::getWaitMinutes)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double priorityAvg = records.stream()
                .filter(r -> r.priority() == TokenPriority.PRIORITY)
                .map(TokenTimeRecord::getWaitMinutes)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double vipAvg = records.stream()
                .filter(r -> r.priority() == TokenPriority.VIP)
                .map(TokenTimeRecord::getWaitMinutes)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return new WaitTimeDistributionResponse(
                round(p50),
                round(p75),
                round(p90),
                round(p95),
                round(avgWait),
                round(maxWait),
                round(normalAvg),
                round(priorityAvg),
                round(vipAvg)
        );
    }

    public static double calculateMedian(List<Double> sortedList) {
        if (sortedList == null || sortedList.isEmpty()) return 0.0;
        int size = sortedList.size();
        if (size % 2 == 1) {
            return sortedList.get(size / 2);
        } else {
            return (sortedList.get((size / 2) - 1) + sortedList.get(size / 2)) / 2.0;
        }
    }

    public static double calculatePercentile(List<Double> sortedList, double percentile) {
        if (sortedList == null || sortedList.isEmpty()) return 0.0;
        if (percentile <= 0.0) return sortedList.get(0);
        if (percentile >= 1.0) return sortedList.get(sortedList.size() - 1);

        double index = percentile * (sortedList.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sortedList.get(lower);
        double weight = index - lower;
        return sortedList.get(lower) * (1.0 - weight) + sortedList.get(upper) * weight;
    }

    public static double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
