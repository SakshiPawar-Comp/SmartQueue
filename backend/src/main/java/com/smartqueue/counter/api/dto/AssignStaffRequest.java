package com.smartqueue.counter.api.dto;

import java.util.UUID;

public class AssignStaffRequest {
    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
