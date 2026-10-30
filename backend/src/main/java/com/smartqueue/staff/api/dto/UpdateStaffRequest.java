package com.smartqueue.staff.api.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public class UpdateStaffRequest {
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    private String phone;
    private UUID branchId;
    private String preferredLanguage;
    private String status;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
