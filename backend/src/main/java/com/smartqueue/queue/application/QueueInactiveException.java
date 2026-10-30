package com.smartqueue.queue.application;

public class QueueInactiveException extends RuntimeException {
    public QueueInactiveException(String message) {
        super(message);
    }
}
