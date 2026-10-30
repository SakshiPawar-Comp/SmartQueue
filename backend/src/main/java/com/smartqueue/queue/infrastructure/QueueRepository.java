package com.smartqueue.queue.infrastructure;

import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public class QueueRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Queue save(Queue queue) {
        if (queue.getId() == null) {
            queue.setId(UUID.randomUUID());
            queue.setCreatedAt(java.time.ZonedDateTime.now());
        }
        queue.setUpdatedAt(java.time.ZonedDateTime.now());
        return entityManager.merge(queue);
    }

    @Transactional
    public void deleteById(UUID id) {
        entityManager.createQuery("DELETE FROM Queue q WHERE q.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    public Queue findById(UUID id) {
        return entityManager.find(Queue.class, id);
    }

    public java.util.List<Queue> findAll() {
        return entityManager.createQuery("SELECT q FROM Queue q ORDER BY q.name", Queue.class)
                .getResultList();
    }

    public Queue findByIdWithLock(UUID id) {
        return entityManager.find(Queue.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    public boolean existsByOrganizationIdAndName(UUID organizationId, String name) {
        var query = entityManager.createQuery(
                "SELECT COUNT(q) > 0 FROM Queue q WHERE q.organizationId = :orgId AND q.name = :name",
                Boolean.class);
        query.setParameter("orgId", organizationId);
        query.setParameter("name", name);
        return query.getSingleResult();
    }

    public java.util.List<Queue> findByOrganizationId(UUID organizationId) {
        var query = entityManager.createQuery(
                "SELECT q FROM Queue q WHERE q.organizationId = :orgId ORDER BY q.name", Queue.class);
        query.setParameter("orgId", organizationId);
        return query.getResultList();
    }

    public long countByOrganizationIdAndStatus(UUID organizationId, QueueStatus status) {
        var query = entityManager.createQuery(
                "SELECT COUNT(q) FROM Queue q WHERE q.organizationId = :orgId AND q.status = :status",
                Long.class);
        query.setParameter("orgId", organizationId);
        query.setParameter("status", status);
        return query.getSingleResult();
    }

    public java.util.List<Queue> findByStatus(QueueStatus status) {
        var query = entityManager.createQuery(
                "SELECT q FROM Queue q WHERE q.status = :status ORDER BY q.name", Queue.class);
        query.setParameter("status", status);
        return query.getResultList();
    }

    public java.util.List<Queue> findByBranchId(UUID branchId) {
        var query = entityManager.createQuery(
                "SELECT q FROM Queue q WHERE q.branchId = :branchId ORDER BY q.name", Queue.class);
        query.setParameter("branchId", branchId);
        return query.getResultList();
    }

    public java.util.List<Queue> findByServiceId(UUID serviceId) {
        var query = entityManager.createQuery(
                "SELECT q FROM Queue q WHERE q.serviceId = :serviceId ORDER BY q.name", Queue.class);
        query.setParameter("serviceId", serviceId);
        return query.getResultList();
    }

    public java.util.List<Queue> findByCriteria(UUID organizationId, UUID branchId, UUID serviceId, QueueStatus status) {
        StringBuilder jpql = new StringBuilder("SELECT q FROM Queue q WHERE 1=1");
        if (organizationId != null) {
            jpql.append(" AND q.organizationId = :organizationId");
        }
        if (branchId != null) {
            jpql.append(" AND q.branchId = :branchId");
        }
        if (serviceId != null) {
            jpql.append(" AND q.serviceId = :serviceId");
        }
        if (status != null) {
            jpql.append(" AND q.status = :status");
        }
        jpql.append(" ORDER BY q.name");

        var query = entityManager.createQuery(jpql.toString(), Queue.class);
        if (organizationId != null) {
            query.setParameter("organizationId", organizationId);
        }
        if (branchId != null) {
            query.setParameter("branchId", branchId);
        }
        if (serviceId != null) {
            query.setParameter("serviceId", serviceId);
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getResultList();
    }
}