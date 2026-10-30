package com.smartqueue.service.application;

import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.service.api.dto.CreateServiceRequest;
import com.smartqueue.service.api.dto.UpdateServiceRequest;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceAdminService {

    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;

    public ServiceAdminService(
            ServiceRepository serviceRepository,
            OrganizationRepository organizationRepository,
            BranchRepository branchRepository) {
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
    }

    public List<Service> listServicesByOrg(UUID organizationId, UUID branchId) {
        if (branchId != null) {
            return serviceRepository.findByOrganizationIdAndBranchIdOrBranchNull(organizationId, branchId);
        }
        return serviceRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public Service createService(UUID organizationId, CreateServiceRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
            if (!branch.getOrganization().getId().equals(organizationId)) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
        }

        Service service = new Service();
        service.setId(UUID.randomUUID());
        service.setOrganization(organization);
        service.setBranch(branch);
        service.setName(request.getName().trim());
        service.setCode(request.getCode() != null ? request.getCode().trim().toUpperCase() : null);
        service.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        service.setStatus("ACTIVE");
        if (request.getRequiresDocumentVerification() != null) {
            service.setRequiresDocumentVerification(request.getRequiresDocumentVerification());
        }
        if (request.getRequiredDocumentTypes() != null && !request.getRequiredDocumentTypes().isBlank()) {
            service.setRequiredDocumentTypes(request.getRequiredDocumentTypes().trim());
        }

        return serviceRepository.save(service);
    }

    @Transactional
    public Service updateService(UUID organizationId, UUID serviceId, UpdateServiceRequest request) {
        Service service = serviceRepository.findById(serviceId);
        if (service == null) {
            throw new IllegalArgumentException("Service not found");
        }

        if (!service.getOrganization().getId().equals(organizationId)) {
            throw new TenantViolationException("Service does not belong to your organization");
        }

        if (request.getBranchId() != null) {
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
            if (!branch.getOrganization().getId().equals(organizationId)) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
            service.setBranch(branch);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            service.setName(request.getName().trim());
        }
        if (request.getCode() != null) {
            service.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getDescription() != null) {
            service.setDescription(request.getDescription().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new IllegalArgumentException("Invalid status: must be ACTIVE or INACTIVE");
            }
            service.setStatus(status);
        }
        if (request.getRequiresDocumentVerification() != null) {
            service.setRequiresDocumentVerification(request.getRequiresDocumentVerification());
        }
        if (request.getRequiredDocumentTypes() != null && !request.getRequiredDocumentTypes().isBlank()) {
            service.setRequiredDocumentTypes(request.getRequiredDocumentTypes().trim());
        }

        return serviceRepository.save(service);
    }
}
