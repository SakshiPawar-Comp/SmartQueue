package com.smartqueue.queue.infrastructure;

import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class QueueTokenRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public QueueToken save(QueueToken token) {
        if (token.getId() == null) {
            token.setId(java.util.UUID.randomUUID());
            token.setCreatedAt(java.time.ZonedDateTime.now());
        }
        token.setUpdatedAt(java.time.ZonedDateTime.now());
        return entityManager.merge(token);
    }

    @Transactional
    public void deleteById(UUID id) {
        entityManager.createQuery("DELETE FROM QueueToken t WHERE t.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional
    public void deleteByQueueId(UUID queueId) {
        entityManager.createQuery("DELETE FROM QueueToken t WHERE t.queue.id = :queueId")
                .setParameter("queueId", queueId)
                .executeUpdate();
    }

    public QueueToken findById(UUID id) {
        return entityManager.find(QueueToken.class, id);
    }

    public QueueToken findByIdWithLock(UUID id) {
        return entityManager.find(QueueToken.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    public java.util.Optional<QueueToken> findActiveTokenByCustomerIdAndQueueId(UUID customerId, UUID queueId) {
        var query = entityManager.createQuery(
                "SELECT t FROM QueueToken t " +
                "WHERE t.customer.id = :customerId " +
                "AND t.queue.id = :queueId " +
                "AND t.status IN (:activeStatuses)",
                QueueToken.class);
        query.setParameter("customerId", customerId);
        query.setParameter("queueId", queueId);
        query.setParameter("activeStatuses", java.util.List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.HOLD));
        var list = query.getResultList();
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
    }

    public java.util.Optional<QueueToken> findFirstWaitingTokenByQueueIdWithLock(UUID queueId) {
        return findFirstWaitingTokenByQueueIdWithLock(queueId, false);
    }

    public java.util.Optional<QueueToken> findFirstWaitingTokenByQueueIdWithLock(UUID queueId, boolean isPriorityEnabled) {
        String jpql;
        if (isPriorityEnabled) {
            jpql = "SELECT t FROM QueueToken t " +
                    "WHERE t.queue.id = :queueId AND t.status = :status " +
                    "ORDER BY CASE t.priority WHEN com.smartqueue.queue.domain.TokenPriority.VIP THEN 0 WHEN com.smartqueue.queue.domain.TokenPriority.PRIORITY THEN 1 ELSE 2 END ASC, t.createdAt ASC";
        } else {
            jpql = "SELECT t FROM QueueToken t " +
                    "WHERE t.queue.id = :queueId AND t.status = :status " +
                    "ORDER BY t.createdAt ASC";
        }

        var query = entityManager.createQuery(jpql, QueueToken.class);
        query.setParameter("queueId", queueId);
        query.setParameter("status", TokenStatus.WAITING);
        query.setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        query.setMaxResults(1);
        var list = query.getResultList();
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
    }

    public QueueToken findByQueueIdAndTokenNumber(UUID queueId, String tokenNumber) {
        var query = entityManager.createQuery(
                "SELECT t FROM QueueToken t WHERE t.queue.id = :queueId AND t.tokenNumber = :tokenNumber",
                QueueToken.class);
        query.setParameter("queueId", queueId);
        query.setParameter("tokenNumber", tokenNumber);
        return query.getSingleResult();
    }

    public java.util.List<QueueToken> findByQueueId(UUID queueId) {
        var query = entityManager.createQuery(
                "SELECT t FROM QueueToken t WHERE t.queue.id = :queueId ORDER BY t.createdAt ASC", QueueToken.class);
        query.setParameter("queueId", queueId);
        return query.getResultList();
    }

    public long countByQueueIdAndStatus(UUID queueId, TokenStatus status) {
        var query = entityManager.createQuery(
                "SELECT COUNT(t) FROM QueueToken t WHERE t.queue.id = :queueId AND t.status = :status",
                Long.class);
        query.setParameter("queueId", queueId);
        query.setParameter("status", status);
        return query.getSingleResult();
    }

    public long countWaitingByQueueId(UUID queueId) {
        return countByQueueIdAndStatus(queueId, TokenStatus.WAITING);
    }

    public long countTokensAhead(UUID queueId, UUID tokenId, java.time.ZonedDateTime createdAt) {
        QueueToken targetToken = entityManager.find(QueueToken.class, tokenId);
        String tokenNum = targetToken != null && targetToken.getTokenNumber() != null ? targetToken.getTokenNumber() : "";
        var query = entityManager.createQuery(
                "SELECT COUNT(t) FROM QueueToken t " +
                "WHERE t.queue.id = :queueId " +
                "AND t.status = :waitingStatus " +
                "AND t.id != :tokenId " +
                "AND t.tokenNumber < :tokenNum",
                Long.class);
        query.setParameter("queueId", queueId);
        query.setParameter("waitingStatus", TokenStatus.WAITING);
        query.setParameter("tokenId", tokenId);
        query.setParameter("tokenNum", tokenNum);
        return query.getSingleResult();
    }

    public long countTokensAhead(UUID queueId, UUID tokenId, com.smartqueue.queue.domain.TokenPriority priority, java.time.ZonedDateTime createdAt, boolean isPriorityEnabled) {
        if (!isPriorityEnabled || priority == null) {
            return countTokensAhead(queueId, tokenId, createdAt);
        }

        QueueToken targetToken = entityManager.find(QueueToken.class, tokenId);
        String tokenNum = targetToken != null && targetToken.getTokenNumber() != null ? targetToken.getTokenNumber() : "";

        int targetRank = priority.getRank();
        var query = entityManager.createQuery(
                "SELECT COUNT(t) FROM QueueToken t " +
                "WHERE t.queue.id = :queueId " +
                "AND t.status = :waitingStatus " +
                "AND t.id != :tokenId " +
                "AND (" +
                "   (CASE t.priority WHEN com.smartqueue.queue.domain.TokenPriority.VIP THEN 0 WHEN com.smartqueue.queue.domain.TokenPriority.PRIORITY THEN 1 ELSE 2 END < :targetRank) " +
                "   OR ((CASE t.priority WHEN com.smartqueue.queue.domain.TokenPriority.VIP THEN 0 WHEN com.smartqueue.queue.domain.TokenPriority.PRIORITY THEN 1 ELSE 2 END = :targetRank) AND t.tokenNumber < :tokenNum)" +
                ")",
                Long.class);
        query.setParameter("queueId", queueId);
        query.setParameter("waitingStatus", TokenStatus.WAITING);
        query.setParameter("tokenId", tokenId);
        query.setParameter("targetRank", targetRank);
        query.setParameter("tokenNum", tokenNum);
        return query.getSingleResult();
    }

    public long countTokensAhead(UUID queueId, java.time.ZonedDateTime createdAt) {
        return countTokensAhead(queueId, UUID.randomUUID(), createdAt);
    }

    public java.util.List<QueueToken> findActiveTokensByCustomerId(UUID customerId) {
        var query = entityManager.createQuery(
                "SELECT t FROM QueueToken t " +
                "JOIN FETCH t.queue q " +
                "WHERE t.customer.id = :customerId " +
                "AND t.status IN (:activeStatuses) " +
                "ORDER BY t.createdAt DESC",
                QueueToken.class);
        query.setParameter("customerId", customerId);
        query.setParameter("activeStatuses", java.util.List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.HOLD));
        return query.getResultList();
    }

    public java.util.List<QueueToken> findHistoryByCustomerId(UUID customerId, TokenStatus statusFilter, int page, int size) {
        StringBuilder jpql = new StringBuilder(
                "SELECT t FROM QueueToken t " +
                "JOIN FETCH t.queue q " +
                "WHERE t.customer.id = :customerId");
        if (statusFilter != null) {
            jpql.append(" AND t.status = :status");
        } else {
            jpql.append(" AND t.status IN (:terminalStatuses)");
        }
        jpql.append(" ORDER BY t.createdAt DESC");

        var query = entityManager.createQuery(jpql.toString(), QueueToken.class);
        query.setParameter("customerId", customerId);
        if (statusFilter != null) {
            query.setParameter("status", statusFilter);
        } else {
            query.setParameter("terminalStatuses", java.util.List.of(TokenStatus.COMPLETED, TokenStatus.CANCELLED, TokenStatus.SKIPPED));
        }
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    public long countHistoryByCustomerId(UUID customerId, TokenStatus statusFilter) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(t) FROM QueueToken t " +
                "WHERE t.customer.id = :customerId");
        if (statusFilter != null) {
            jpql.append(" AND t.status = :status");
        } else {
            jpql.append(" AND t.status IN (:terminalStatuses)");
        }

        var query = entityManager.createQuery(jpql.toString(), Long.class);
        query.setParameter("customerId", customerId);
        if (statusFilter != null) {
            query.setParameter("status", statusFilter);
        } else {
            query.setParameter("terminalStatuses", java.util.List.of(TokenStatus.COMPLETED, TokenStatus.CANCELLED, TokenStatus.SKIPPED));
        }
        return query.getSingleResult();
    }
}