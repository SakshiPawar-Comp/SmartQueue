package com.smartqueue.service.api.dto;

import com.smartqueue.service.domain.Service;
import java.util.UUID;

public class ServiceResponse {
    private UUID id;
    private UUID organizationId;
    private UUID branchId;
    private String name;
    private String code;
    private String description;
    private String status;
    private Boolean requiresDocumentVerification;
    private String requiredDocumentTypes;

    public static ServiceResponse fromEntity(Service service) {
        ServiceResponse dto = new ServiceResponse();
        dto.id = service.getId();
        dto.organizationId = service.getOrganization() != null ? service.getOrganization().getId() : null;
        dto.branchId = service.getBranch() != null ? service.getBranch().getId() : null;
        dto.name = service.getName();
        dto.code = service.getCode();
        dto.description = service.getDescription();
        dto.status = service.getStatus();
        dto.requiresDocumentVerification = service.getRequiresDocumentVerification();
        dto.requiredDocumentTypes = service.getRequiredDocumentTypes();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getRequiresDocumentVerification() { return requiresDocumentVerification != null ? requiresDocumentVerification : false; }
    public void setRequiresDocumentVerification(Boolean requiresDocumentVerification) { this.requiresDocumentVerification = requiresDocumentVerification; }

    public String getRequiredDocumentTypes() { return requiredDocumentTypes != null ? requiredDocumentTypes : "GOVERNMENT_ID"; }
    public void setRequiredDocumentTypes(String requiredDocumentTypes) { this.requiredDocumentTypes = requiredDocumentTypes; }
}
