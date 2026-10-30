package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NotificationProviderRegistry {

    private final Map<NotificationChannel, NotificationProvider> providers = new EnumMap<>(NotificationChannel.class);

    public NotificationProviderRegistry(List<NotificationProvider> providerList) {
        // Sort or register so that non-Log providers take precedence
        for (NotificationProvider provider : providerList) {
            NotificationChannel ch = provider.getChannel();
            if (!providers.containsKey(ch) || !(provider instanceof LogEmailNotificationProvider)) {
                providers.put(ch, provider);
            }
        }
    }

    public Optional<NotificationProvider> getProvider(NotificationChannel channel) {
        return Optional.ofNullable(providers.get(channel));
    }
}
