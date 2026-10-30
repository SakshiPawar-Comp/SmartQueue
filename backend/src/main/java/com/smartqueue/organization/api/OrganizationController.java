package com.smartqueue.organization.api;

import com.smartqueue.branch.api.dto.BranchResponse;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.organization.api.dto.OrganizationResponse;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.service.api.dto.ServiceResponse;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;

    public OrganizationController(
            OrganizationRepository organizationRepository,
            BranchRepository branchRepository,
            ServiceRepository serviceRepository) {
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
    }

    /** GET /api/organizations - List all active organizations */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationResponse>>> getOrganizations() {
        List<Organization> orgs = organizationRepository.findByStatus("ACTIVE");
        List<OrganizationResponse> responses = orgs.stream().map(OrganizationResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }

    /** GET /api/organizations/{orgId} - Get organization by ID */
    @GetMapping("/{orgId}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganizationById(@PathVariable UUID orgId) {
        return organizationRepository.findById(orgId)
                .map(org -> ResponseEntity.ok(ApiResponse.success(OrganizationResponse.fromEntity(org), MDC.get("correlationId"))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/organizations/{orgId}/branches - Get branches for organization */
    @GetMapping("/{orgId}/branches")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getBranchesByOrganization(@PathVariable UUID orgId) {
        List<Branch> branches = branchRepository.findByOrganizationIdAndStatus(orgId, "ACTIVE");
        List<BranchResponse> responses = branches.stream().map(BranchResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }

    /** GET /api/organizations/{orgId}/services - Get services for organization */
    @GetMapping("/{orgId}/services")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServicesByOrganization(@PathVariable UUID orgId) {
        var services = serviceRepository.findByOrganizationId(orgId);
        List<ServiceResponse> responses = services.stream().map(ServiceResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }
}
