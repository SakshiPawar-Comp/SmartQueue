package com.smartqueue.counter.infrastructure;

import com.smartqueue.counter.domain.StaffCounterAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffCounterAssignmentRepository extends JpaRepository<StaffCounterAssignment, UUID> {

    List<StaffCounterAssignment> findByUserIdAndStatus(UUID userId, String status);

    List<StaffCounterAssignment> findByCounterIdAndStatus(UUID counterId, String status);

    List<StaffCounterAssignment> findByOrganizationIdAndStatus(UUID organizationId, String status);

    List<StaffCounterAssignment> findByBranchIdAndStatus(UUID branchId, String status);

    @Query("SELECT a FROM StaffCounterAssignment a WHERE a.user.id = :userId AND a.status = 'ACTIVE'")
    Optional<StaffCounterAssignment> findActiveByUserId(@Param("userId") UUID userId);
}
