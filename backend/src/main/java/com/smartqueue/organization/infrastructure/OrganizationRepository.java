package com.smartqueue.organization.infrastructure;

import com.smartqueue.organization.domain.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlug(String slug);
    Optional<Organization> findBySlugIgnoreCase(String slug);
    Optional<Organization> findByNameIgnoreCase(String name);
    List<Organization> findByStatus(String status);
}


