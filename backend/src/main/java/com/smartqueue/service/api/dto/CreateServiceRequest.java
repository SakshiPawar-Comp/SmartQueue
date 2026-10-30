package com.smartqueue.service.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class CreateServiceRequest {
    @NotBlank(message = "Service name is required")
    @Size(max = 255, message = "Service name must not exceed 255 characters")
    private String name;

    @Size(max = 50, message = "Service code must not exceed 50 characters")
    private String code;

    private String description;
    private UUID branchId;
    private Boolean requiresDocumentVerification;
    private String requiredDocumentTypes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public Boolean getRequiresDocumentVerification() {
        return requiresDocumentVerification;
    }

    public void setRequiresDocumentVerification(Boolean requiresDocumentVerification) {
        this.requiresDocumentVerification = requiresDocumentVerification;
    }

    public String getRequiredDocumentTypes() {
        return requiredDocumentTypes;
    }

    public void setRequiredDocumentTypes(String requiredDocumentTypes) {
        this.requiredDocumentTypes = requiredDocumentTypes;
    }
}
