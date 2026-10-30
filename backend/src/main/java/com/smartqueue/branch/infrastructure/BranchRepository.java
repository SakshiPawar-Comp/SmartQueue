package com.smartqueue.branch.infrastructure;

import com.smartqueue.branch.domain.Branch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByOrganizationId(UUID organizationId);
    List<Branch> findByOrganizationIdAndStatus(UUID organizationId, String status);
    java.util.Optional<Branch> findByOrganizationIdAndCode(UUID organizationId, String code);
}

