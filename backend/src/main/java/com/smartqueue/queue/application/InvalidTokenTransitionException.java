package com.smartqueue.queue.application;

public class InvalidTokenTransitionException extends RuntimeException {
    public InvalidTokenTransitionException(String message) {
        super(message);
    }
}
