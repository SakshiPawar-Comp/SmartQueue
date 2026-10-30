package com.smartqueue.queue.application;

public class QueueNotFoundException extends RuntimeException {
    public QueueNotFoundException(String message) {
        super(message);
    }
}
