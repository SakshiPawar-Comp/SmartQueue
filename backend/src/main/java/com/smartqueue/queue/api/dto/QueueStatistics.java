package com.smartqueue.queue.api.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public class QueueStatistics {
    private long totalTokens;
    private long waitingTokens;
    private long calledTokens;
    private long completedTokens;
    private long holdTokens;
    private long skippedTokens;

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getWaitingTokens() {
        return waitingTokens;
    }

    public void setWaitingTokens(long waitingTokens) {
        this.waitingTokens = waitingTokens;
    }

    public long getCalledTokens() {
        return calledTokens;
    }

    public void setCalledTokens(long calledTokens) {
        this.calledTokens = calledTokens;
    }

    public long getCompletedTokens() {
        return completedTokens;
    }

    public void setCompletedTokens(long completedTokens) {
        this.completedTokens = completedTokens;
    }

    public long getHoldTokens() {
        return holdTokens;
    }

    public void setHoldTokens(long holdTokens) {
        this.holdTokens = holdTokens;
    }

    public long getSkippedTokens() {
        return skippedTokens;
    }

    public void setSkippedTokens(long skippedTokens) {
        this.skippedTokens = skippedTokens;
    }
}