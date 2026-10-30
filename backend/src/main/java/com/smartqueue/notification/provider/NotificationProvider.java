package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;

public interface NotificationProvider {
    NotificationChannel getChannel();
    ProviderResult send(Notification notification);
}
