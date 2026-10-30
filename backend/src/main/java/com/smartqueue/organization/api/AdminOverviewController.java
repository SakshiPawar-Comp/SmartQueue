package com.smartqueue.organization.api;

import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.organization.api.dto.AdminOverviewResponse;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
public class AdminOverviewController {

    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final CounterRepository counterRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminOverviewController(
            BranchRepository branchRepository,
            ServiceRepository serviceRepository,
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository,
            CounterRepository counterRepository,
            UserRoleRepository userRoleRepository) {
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.counterRepository = counterRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** GET /api/admin/overview - Operational summary for organization admin */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> getOverview(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();

        List<Branch> branches = branchRepository.findByOrganizationId(orgId);
        long totalBranches = branches.size();
        long activeBranches = branches.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).count();

        List<Service> services = serviceRepository.findByOrganizationId(orgId);
        long totalServices = services.size();
        long activeServices = services.stream().filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus())).count();

        List<Queue> queues = queueRepository.findByOrganizationId(orgId);
        long totalQueues = queues.size();
        long openQueues = queues.stream().filter(q -> q.getStatus() == QueueStatus.ACTIVE).count();

        List<Counter> counters = counterRepository.findByOrganizationId(orgId);
        long totalCounters = counters.size();
        long activeCounters = counters.stream().filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus())).count();

        List<UserRole> staffRoles = userRoleRepository.findByOrganizationIdAndRoleCode(orgId, "STAFF");
        long totalStaff = staffRoles.size();
        long activeStaff = staffRoles.stream()
                .filter(ur -> ur.getUser() != null && "ACTIVE".equalsIgnoreCase(ur.getUser().getStatus()))
                .count();

        long waitingTokens = 0;
        long inProgressTokens = 0;
        for (Queue q : queues) {
            waitingTokens += queueTokenRepository.countByQueueIdAndStatus(q.getId(), TokenStatus.WAITING);
            inProgressTokens += queueTokenRepository.countByQueueIdAndStatus(q.getId(), TokenStatus.CALLED)
                    + queueTokenRepository.countByQueueIdAndStatus(q.getId(), TokenStatus.HOLD);
        }

        AdminOverviewResponse response = new AdminOverviewResponse(
                totalBranches,
                activeBranches,
                totalServices,
                activeServices,
                totalQueues,
                openQueues,
                totalCounters,
                activeCounters,
                totalStaff,
                activeStaff,
                waitingTokens,
                inProgressTokens
        );

        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }
}
