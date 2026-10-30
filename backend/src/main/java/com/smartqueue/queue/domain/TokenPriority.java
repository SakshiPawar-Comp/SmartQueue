package com.smartqueue.queue.domain;

public enum TokenPriority {
    VIP(0),
    PRIORITY(1),
    NORMAL(2);

    private final int rank;

    TokenPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
