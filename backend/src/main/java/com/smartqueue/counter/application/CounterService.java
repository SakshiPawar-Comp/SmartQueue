package com.smartqueue.counter.application;

import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.counter.api.dto.CounterResponse;
import com.smartqueue.counter.api.dto.CreateCounterRequest;
import com.smartqueue.counter.api.dto.UpdateCounterRequest;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.domain.CounterQueueAssignment;
import com.smartqueue.counter.domain.StaffCounterAssignment;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.counter.infrastructure.StaffCounterAssignmentRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.infrastructure.QueueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CounterService {

    private final CounterRepository counterRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final QueueRepository queueRepository;
    private final StaffCounterAssignmentRepository staffCounterAssignmentRepository;
    private final CounterQueueAssignmentRepository counterQueueAssignmentRepository;

    public CounterService(
            CounterRepository counterRepository,
            OrganizationRepository organizationRepository,
            BranchRepository branchRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            QueueRepository queueRepository,
            StaffCounterAssignmentRepository staffCounterAssignmentRepository,
            CounterQueueAssignmentRepository counterQueueAssignmentRepository) {
        this.counterRepository = counterRepository;
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.queueRepository = queueRepository;
        this.staffCounterAssignmentRepository = staffCounterAssignmentRepository;
        this.counterQueueAssignmentRepository = counterQueueAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CounterResponse> listCounters(UUID organizationId, UUID branchId) {
        List<Counter> counters = (branchId != null)
                ? counterRepository.findByBranchId(branchId)
                : counterRepository.findByOrganizationId(organizationId);

        return counters.stream().map(this::toCounterResponse).toList();
    }

    @Transactional(readOnly = true)
    public CounterResponse getCounterResponse(UUID organizationId, UUID counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Counter not found"));

        if (!counter.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Counter does not belong to your organization");
        }

        return toCounterResponse(counter);
    }

    public Counter findCounterById(UUID counterId) {
        return counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Counter not found"));
    }

    @Transactional
    public CounterResponse createCounter(UUID organizationId, CreateCounterRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));

        if (!branch.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Branch does not belong to your organization");
        }

        Counter counter = new Counter(
                organization,
                branch,
                request.getName().trim(),
                request.getCode() != null ? request.getCode().trim().toUpperCase() : null
        );
        counter.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        counter.setStatus("ACTIVE");
        counter = counterRepository.save(counter);

        return toCounterResponse(counter);
    }

    @Transactional
    public CounterResponse updateCounter(UUID organizationId, UUID counterId, UpdateCounterRequest request) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Counter not found"));

        if (!counter.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Counter does not belong to your organization");
        }

        if (request.getBranchId() != null && !request.getBranchId().equals(counter.getBranch().getId())) {
            Branch newBranch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
            if (!newBranch.getOrganization().getId().equals(organizationId)) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
            counter.setBranch(newBranch);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            counter.setName(request.getName().trim());
        }
        if (request.getCode() != null) {
            counter.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getDescription() != null) {
            counter.setDescription(request.getDescription().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new IllegalArgumentException("Invalid status: must be ACTIVE or INACTIVE");
            }
            counter.setStatus(status);
        }

        counter.setUpdatedAt(ZonedDateTime.now());
        counter = counterRepository.save(counter);

        return toCounterResponse(counter);
    }

    @Transactional
    public CounterResponse assignStaffToCounter(UUID organizationId, UUID counterId, UUID staffUserId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Counter not found"));

        if (!counter.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Counter does not belong to your organization");
        }

        // Deactivate any existing active assignment on this counter
        List<StaffCounterAssignment> existingCounterAssignments =
                staffCounterAssignmentRepository.findByCounterIdAndStatus(counterId, "ACTIVE");
        for (StaffCounterAssignment ca : existingCounterAssignments) {
            ca.setStatus("INACTIVE");
            ca.setUnassignedAt(ZonedDateTime.now());
            ca.setUpdatedAt(ZonedDateTime.now());
            staffCounterAssignmentRepository.save(ca);
        }

        if (staffUserId != null) {
            User staffUser = userRepository.findById(staffUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

            // Verify staff user has STAFF role in this organization
            List<UserRole> roles = userRoleRepository.findByUserId(staffUserId);
            boolean isStaffInOrg = roles.stream().anyMatch(ur ->
                    ur.getRole().getCode().equals("STAFF") &&
                    ur.getOrganization() != null &&
                    ur.getOrganization().getId().equals(organizationId));

            if (!isStaffInOrg) {
                throw new TenantViolationException("User is not an active staff member of this organization");
            }

            // Deactivate any existing assignment for this staff user
            Optional<StaffCounterAssignment> previousStaffAssignment =
                    staffCounterAssignmentRepository.findActiveByUserId(staffUserId);
            previousStaffAssignment.ifPresent(sa -> {
                sa.setStatus("INACTIVE");
                sa.setUnassignedAt(ZonedDateTime.now());
                sa.setUpdatedAt(ZonedDateTime.now());
                staffCounterAssignmentRepository.save(sa);
            });

            // Create new assignment
            StaffCounterAssignment newAssignment = new StaffCounterAssignment(
                    counter.getOrganization(),
                    counter.getBranch(),
                    counter,
                    staffUser
            );
            staffCounterAssignmentRepository.save(newAssignment);
        }

        return toCounterResponse(counter);
    }

    @Transactional
    public CounterResponse assignQueuesToCounter(UUID organizationId, UUID counterId, List<UUID> queueIds) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Counter not found"));

        if (!counter.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Counter does not belong to your organization");
        }

        // Remove previous queue assignments
        counterQueueAssignmentRepository.deleteByCounterId(counterId);

        if (queueIds != null && !queueIds.isEmpty()) {
            for (UUID qId : queueIds) {
                Queue queue = queueRepository.findById(qId);
                if (queue == null) {
                    throw new IllegalArgumentException("Queue not found: " + qId);
                }

                if (!queue.getOrganizationId().equals(organizationId)) {
                    throw new TenantViolationException("Queue " + qId + " does not belong to your organization");
                }

                // If queue is branch-bound, verify it matches counter's branch
                if (queue.getBranchId() != null && !queue.getBranchId().equals(counter.getBranch().getId())) {
                    throw new IllegalArgumentException("Queue " + queue.getName() + " is assigned to another branch");
                }

                CounterQueueAssignment cqa = new CounterQueueAssignment(counter, queue);
                counterQueueAssignmentRepository.save(cqa);
            }
        }

        return toCounterResponse(counter);
    }

    private CounterResponse toCounterResponse(Counter counter) {
        UUID assignedStaffUserId = null;
        String assignedStaffName = null;

        List<StaffCounterAssignment> activeStaff = staffCounterAssignmentRepository.findByCounterIdAndStatus(counter.getId(), "ACTIVE");
        if (!activeStaff.isEmpty()) {
            StaffCounterAssignment sca = activeStaff.get(0);
            assignedStaffUserId = sca.getUser().getId();
            assignedStaffName = sca.getUser().getFullName();
        }

        List<CounterQueueAssignment> cqas = counterQueueAssignmentRepository.findByCounterId(counter.getId());
        List<UUID> assignedQueueIds = cqas.stream().map(cqa -> cqa.getQueue().getId()).toList();

        return CounterResponse.fromEntity(counter, assignedStaffUserId, assignedStaffName, assignedQueueIds);
    }
}
