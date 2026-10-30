package com.smartqueue.notification.provider;

public record ProviderResult(boolean success, String providerMessageId, String errorMessage) {
    public static ProviderResult sent(String providerMessageId) {
        return new ProviderResult(true, providerMessageId, null);
    }

    public static ProviderResult failed(String errorMessage) {
        return new ProviderResult(false, null, errorMessage);
    }
}
