package com.smartqueue.service.infrastructure;

import com.smartqueue.service.domain.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ServiceRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Service save(Service service) {
        if (service.getId() == null) {
            service.setId(UUID.randomUUID());
            service.setCreatedAt(ZonedDateTime.now());
        }
        service.setUpdatedAt(ZonedDateTime.now());
        return entityManager.merge(service);
    }

    public Service findById(UUID id) {
        return entityManager.find(Service.class, id);
    }

    public List<Service> findAll() {
        return entityManager.createQuery("SELECT s FROM Service s ORDER BY s.name", Service.class)
                .getResultList();
    }

    public List<Service> findByOrganizationId(UUID organizationId) {
        return entityManager.createQuery(
                "SELECT s FROM Service s WHERE s.organization.id = :orgId AND s.status = 'ACTIVE' ORDER BY s.name",
                Service.class)
                .setParameter("orgId", organizationId)
                .getResultList();
    }

    public List<Service> findByBranchId(UUID branchId) {
        return entityManager.createQuery(
                "SELECT s FROM Service s WHERE s.branch.id = :branchId AND s.status = 'ACTIVE' ORDER BY s.name",
                Service.class)
                .setParameter("branchId", branchId)
                .getResultList();
    }

    public List<Service> findByOrganizationIdAndBranchIdOrBranchNull(UUID organizationId, UUID branchId) {
        return entityManager.createQuery(
                "SELECT s FROM Service s WHERE s.organization.id = :orgId AND (s.branch.id = :branchId OR s.branch IS NULL) AND s.status = 'ACTIVE' ORDER BY s.name",
                Service.class)
                .setParameter("orgId", organizationId)
                .setParameter("branchId", branchId)
                .getResultList();
    }

    @Transactional
    public void deleteById(UUID id) {
        entityManager.createQuery("DELETE FROM Service s WHERE s.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
