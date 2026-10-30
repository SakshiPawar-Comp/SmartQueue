package com.smartqueue.queue.domain;

import com.smartqueue.auth.domain.User;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "queue_tokens")
public class QueueToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "token_number", nullable = false)
    private String tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TokenStatus status;

    @Column(name = "called_at")
    private ZonedDateTime calledAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "held_at")
    private ZonedDateTime heldAt;

    @Column(name = "skipped_at")
    private ZonedDateTime skippedAt;

    @Column(name = "cancelled_at")
    private ZonedDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TokenPriority priority = TokenPriority.NORMAL;

    @Column(name = "served_by_counter_id")
    private UUID servedByCounterId;

    @Column(name = "served_by_user_id")
    private UUID servedByUserId;

    @Column(name = "expected_travel_time_minutes")
    private Integer expectedTravelTimeMinutes;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    // Default constructor for JPA
    public QueueToken() {
        this.id = UUID.randomUUID();
        this.status = TokenStatus.WAITING;
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    public QueueToken(Queue queue, String tokenNumber) {
        this.id = UUID.randomUUID();
        this.queue = queue;
        this.tokenNumber = tokenNumber;
        this.status = TokenStatus.WAITING;
        this.calledAt = null;
        this.completedAt = null;
        this.heldAt = null;
        this.skippedAt = null;
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    public QueueToken(Queue queue, String tokenNumber, User customer) {
        this.id = UUID.randomUUID();
        this.queue = queue;
        this.customer = customer;
        this.tokenNumber = tokenNumber;
        this.status = TokenStatus.WAITING;
        this.calledAt = null;
        this.completedAt = null;
        this.heldAt = null;
        this.skippedAt = null;
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(String tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public ZonedDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(ZonedDateTime calledAt) {
        this.calledAt = calledAt;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public ZonedDateTime getHeldAt() {
        return heldAt;
    }

    public void setHeldAt(ZonedDateTime heldAt) {
        this.heldAt = heldAt;
    }

    public ZonedDateTime getSkippedAt() {
        return skippedAt;
    }

    public void setSkippedAt(ZonedDateTime skippedAt) {
        this.skippedAt = skippedAt;
    }

    public ZonedDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(ZonedDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public TokenPriority getPriority() {
        return priority;
    }

    public void setPriority(TokenPriority priority) {
        this.priority = priority;
    }

    public UUID getServedByCounterId() {
        return servedByCounterId;
    }

    public void setServedByCounterId(UUID servedByCounterId) {
        this.servedByCounterId = servedByCounterId;
    }

    public UUID getServedByUserId() {
        return servedByUserId;
    }

    public void setServedByUserId(UUID servedByUserId) {
        this.servedByUserId = servedByUserId;
    }

    public Integer getExpectedTravelTimeMinutes() {
        return expectedTravelTimeMinutes;
    }

    public void setExpectedTravelTimeMinutes(Integer expectedTravelTimeMinutes) {
        this.expectedTravelTimeMinutes = expectedTravelTimeMinutes;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}