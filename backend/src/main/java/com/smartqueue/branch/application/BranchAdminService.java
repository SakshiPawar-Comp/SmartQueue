package com.smartqueue.branch.application;

import com.smartqueue.branch.api.dto.CreateBranchRequest;
import com.smartqueue.branch.api.dto.UpdateBranchRequest;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BranchAdminService {

    private final BranchRepository branchRepository;
    private final OrganizationRepository organizationRepository;

    public BranchAdminService(BranchRepository branchRepository, OrganizationRepository organizationRepository) {
        this.branchRepository = branchRepository;
        this.organizationRepository = organizationRepository;
    }

    public List<Branch> listBranchesByOrg(UUID organizationId) {
        return branchRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public Branch createBranch(UUID organizationId, CreateBranchRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        if (branchRepository.findByOrganizationIdAndCode(organizationId, request.getCode().trim().toUpperCase()).isPresent()) {
            throw new IllegalArgumentException("Branch code '" + request.getCode() + "' already exists in this organization");
        }

        Branch branch = new Branch();
        branch.setId(UUID.randomUUID());
        branch.setOrganization(organization);
        branch.setName(request.getName().trim());
        branch.setCode(request.getCode().trim().toUpperCase());
        branch.setTimezone(request.getTimezone() != null && !request.getTimezone().isBlank() ? request.getTimezone().trim() : "UTC");
        branch.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        branch.setStatus("ACTIVE");

        return branchRepository.save(branch);
    }

    @Transactional
    public Branch updateBranch(UUID organizationId, UUID branchId, UpdateBranchRequest request) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));

        if (!branch.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Branch does not belong to your organization");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            branch.setName(request.getName().trim());
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (!newCode.equals(branch.getCode())) {
                if (branchRepository.findByOrganizationIdAndCode(organizationId, newCode).isPresent()) {
                    throw new IllegalArgumentException("Branch code '" + newCode + "' already exists in this organization");
                }
                branch.setCode(newCode);
            }
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            branch.setTimezone(request.getTimezone().trim());
        }
        if (request.getAddress() != null) {
            branch.setAddress(request.getAddress().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new IllegalArgumentException("Invalid status: must be ACTIVE or INACTIVE");
            }
            branch.setStatus(status);
        }

        return branchRepository.save(branch);
    }
}
