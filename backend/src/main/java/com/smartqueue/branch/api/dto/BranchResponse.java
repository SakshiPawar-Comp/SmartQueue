package com.smartqueue.branch.api.dto;

import com.smartqueue.branch.domain.Branch;
import java.util.UUID;

public class BranchResponse {
    private UUID id;
    private UUID organizationId;
    private String name;
    private String code;
    private String timezone;
    private String address;
    private String status;

    public static BranchResponse fromEntity(Branch branch) {
        BranchResponse dto = new BranchResponse();
        dto.id = branch.getId();
        dto.organizationId = branch.getOrganization() != null ? branch.getOrganization().getId() : null;
        dto.name = branch.getName();
        dto.code = branch.getCode();
        dto.timezone = branch.getTimezone();
        dto.address = branch.getAddress();
        dto.status = branch.getStatus();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
