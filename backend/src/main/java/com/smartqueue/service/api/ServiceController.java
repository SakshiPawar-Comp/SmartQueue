package com.smartqueue.service.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.queue.api.dto.QueueResponse;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.api.dto.CreateServiceRequest;
import com.smartqueue.service.api.dto.ServiceResponse;
import com.smartqueue.service.api.dto.UpdateServiceRequest;
import com.smartqueue.service.application.ServiceAdminService;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ServiceAdminService serviceAdminService;
    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;

    public ServiceController(
            ServiceRepository serviceRepository,
            ServiceAdminService serviceAdminService,
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository) {
        this.serviceRepository = serviceRepository;
        this.serviceAdminService = serviceAdminService;
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
    }

    /** GET /api/services - List services for organization/branch */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServices(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID branchId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID effectiveOrgId = organizationId != null ? organizationId : TenantContext.getCurrentTenant();
        List<Service> services = effectiveOrgId != null
                ? serviceAdminService.listServicesByOrg(effectiveOrgId, branchId)
                : List.of();
        List<ServiceResponse> responses = services.stream().map(ServiceResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }

    /** GET /api/services/{serviceId} - Get service by ID */
    @GetMapping("/{serviceId}")
    public ResponseEntity<ApiResponse<ServiceResponse>> getServiceById(@PathVariable UUID serviceId) {
        Service service = serviceRepository.findById(serviceId);
        if (service == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(ServiceResponse.fromEntity(service), MDC.get("correlationId")));
    }

    /** POST /api/services - Create new service */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @Valid @RequestBody CreateServiceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        Service service = serviceAdminService.createService(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ServiceResponse.fromEntity(service), MDC.get("correlationId")));
    }

    /** PUT /api/services/{serviceId} - Update service */
    @PutMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        Service service = serviceAdminService.updateService(orgId, serviceId, request);
        return ResponseEntity.ok(ApiResponse.success(ServiceResponse.fromEntity(service), MDC.get("correlationId")));
    }

    /** GET /api/services/{serviceId}/queues - Get queues for service */
    @GetMapping("/{serviceId}/queues")
    public ResponseEntity<ApiResponse<List<QueueResponse>>> getQueuesByService(@PathVariable UUID serviceId) {
        List<Queue> queues = queueRepository.findByServiceId(serviceId);
        List<QueueResponse> responses = queues.stream().map(q -> {
            long waitingCount = queueTokenRepository.countWaitingByQueueId(q.getId());
            int estWait = (int) (waitingCount * (q.getDefaultServiceTimeMinutes() != null ? q.getDefaultServiceTimeMinutes() : 5));
            return QueueResponse.fromEntity(q, waitingCount, estWait);
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }
}
