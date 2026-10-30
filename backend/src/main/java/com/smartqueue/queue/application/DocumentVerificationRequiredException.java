package com.smartqueue.queue.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PRECONDITION_FAILED)
public class DocumentVerificationRequiredException extends RuntimeException {
    public DocumentVerificationRequiredException(String message) {
        super(message);
    }
}
