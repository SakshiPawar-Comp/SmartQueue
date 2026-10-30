package com.smartqueue.common.tenant;

public class TenantViolationException extends RuntimeException {

    public TenantViolationException(String message) {
        super(message);
    }
}
