package com.smartqueue.counter.api.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public class UpdateCounterRequest {
    private UUID branchId;

    @Size(max = 255, message = "Counter name must not exceed 255 characters")
    private String name;

    @Size(max = 50, message = "Counter code must not exceed 50 characters")
    private String code;

    private String description;
    private String status;

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
