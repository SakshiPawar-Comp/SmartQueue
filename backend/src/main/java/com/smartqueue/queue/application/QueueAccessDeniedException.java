package com.smartqueue.queue.application;

public class QueueAccessDeniedException extends RuntimeException {
    public QueueAccessDeniedException(String message) {
        super(message);
    }
}
