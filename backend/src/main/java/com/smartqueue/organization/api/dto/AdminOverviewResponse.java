package com.smartqueue.organization.api.dto;

public class AdminOverviewResponse {
    private long totalBranches;
    private long activeBranches;
    private long totalServices;
    private long activeServices;
    private long totalQueues;
    private long openQueues;
    private long totalCounters;
    private long activeCounters;
    private long totalStaff;
    private long activeStaff;
    private long waitingTokens;
    private long inProgressTokens;

    public AdminOverviewResponse() {}

    public AdminOverviewResponse(
            long totalBranches,
            long activeBranches,
            long totalServices,
            long activeServices,
            long totalQueues,
            long openQueues,
            long totalCounters,
            long activeCounters,
            long totalStaff,
            long activeStaff,
            long waitingTokens,
            long inProgressTokens) {
        this.totalBranches = totalBranches;
        this.activeBranches = activeBranches;
        this.totalServices = totalServices;
        this.activeServices = activeServices;
        this.totalQueues = totalQueues;
        this.openQueues = openQueues;
        this.totalCounters = totalCounters;
        this.activeCounters = activeCounters;
        this.totalStaff = totalStaff;
        this.activeStaff = activeStaff;
        this.waitingTokens = waitingTokens;
        this.inProgressTokens = inProgressTokens;
    }

    public long getTotalBranches() { return totalBranches; }
    public void setTotalBranches(long totalBranches) { this.totalBranches = totalBranches; }

    public long getActiveBranches() { return activeBranches; }
    public void setActiveBranches(long activeBranches) { this.activeBranches = activeBranches; }

    public long getTotalServices() { return totalServices; }
    public void setTotalServices(long totalServices) { this.totalServices = totalServices; }

    public long getActiveServices() { return activeServices; }
    public void setActiveServices(long activeServices) { this.activeServices = activeServices; }

    public long getTotalQueues() { return totalQueues; }
    public void setTotalQueues(long totalQueues) { this.totalQueues = totalQueues; }

    public long getOpenQueues() { return openQueues; }
    public void setOpenQueues(long openQueues) { this.openQueues = openQueues; }

    public long getTotalCounters() { return totalCounters; }
    public void setTotalCounters(long totalCounters) { this.totalCounters = totalCounters; }

    public long getActiveCounters() { return activeCounters; }
    public void setActiveCounters(long activeCounters) { this.activeCounters = activeCounters; }

    public long getTotalStaff() { return totalStaff; }
    public void setTotalStaff(long totalStaff) { this.totalStaff = totalStaff; }

    public long getActiveStaff() { return activeStaff; }
    public void setActiveStaff(long activeStaff) { this.activeStaff = activeStaff; }

    public long getWaitingTokens() { return waitingTokens; }
    public void setWaitingTokens(long waitingTokens) { this.waitingTokens = waitingTokens; }

    public long getInProgressTokens() { return inProgressTokens; }
    public void setInProgressTokens(long inProgressTokens) { this.inProgressTokens = inProgressTokens; }
}
