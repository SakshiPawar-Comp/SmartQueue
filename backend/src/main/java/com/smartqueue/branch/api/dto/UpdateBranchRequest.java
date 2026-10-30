package com.smartqueue.branch.api.dto;

import jakarta.validation.constraints.Size;

public class UpdateBranchRequest {
    @Size(max = 255, message = "Branch name must not exceed 255 characters")
    private String name;

    @Size(max = 50, message = "Branch code must not exceed 50 characters")
    private String code;

    private String timezone;
    private String address;
    private String status;

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

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
