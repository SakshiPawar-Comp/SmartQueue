package com.smartqueue.queue.application;

public class QueueCapacityExceededException extends RuntimeException {
    public QueueCapacityExceededException(String message) {
        super(message);
    }
}
