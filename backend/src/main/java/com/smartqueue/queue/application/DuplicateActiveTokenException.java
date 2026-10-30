package com.smartqueue.queue.application;

public class DuplicateActiveTokenException extends RuntimeException {
    public DuplicateActiveTokenException(String message) {
        super(message);
    }
}
