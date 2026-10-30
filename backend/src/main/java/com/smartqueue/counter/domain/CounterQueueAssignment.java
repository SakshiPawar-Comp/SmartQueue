package com.smartqueue.counter.domain;

import com.smartqueue.queue.domain.Queue;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "counter_queue_assignments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"counter_id", "queue_id"})
})
public class CounterQueueAssignment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counter_id", nullable = false)
    private Counter counter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    public CounterQueueAssignment() {
        this.id = UUID.randomUUID();
        this.createdAt = ZonedDateTime.now();
    }

    public CounterQueueAssignment(Counter counter, Queue queue) {
        this();
        this.counter = counter;
        this.queue = queue;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Counter getCounter() {
        return counter;
    }

    public void setCounter(Counter counter) {
        this.counter = counter;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
