package com.smartqueue.counter.infrastructure;

import com.smartqueue.counter.domain.Counter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CounterRepository extends JpaRepository<Counter, UUID> {

    List<Counter> findByOrganizationId(UUID organizationId);

    List<Counter> findByOrganizationIdAndStatus(UUID organizationId, String status);

    List<Counter> findByBranchId(UUID branchId);

    List<Counter> findByBranchIdAndStatus(UUID branchId, String status);
}
