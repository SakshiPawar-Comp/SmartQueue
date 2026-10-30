package com.smartqueue.counter.api.dto;

import java.util.List;
import java.util.UUID;

public class AssignQueuesRequest {
    private List<UUID> queueIds;

    public List<UUID> getQueueIds() {
        return queueIds;
    }

    public void setQueueIds(List<UUID> queueIds) {
        this.queueIds = queueIds;
    }
}
