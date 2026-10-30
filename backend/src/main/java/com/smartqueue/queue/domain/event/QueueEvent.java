package com.smartqueue.queue.domain.event;

import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.TokenStatus;
import java.time.ZonedDateTime;
import java.util.UUID;

public record QueueEvent(
        UUID queueId,
        UUID organizationId,
        String eventType,
        QueueStatus queueStatus,
        String currentTokenNumber,
        long waitingCount,
        UUID tokenId,
        String tokenNumber,
        TokenStatus tokenStatus,
        ZonedDateTime timestamp
) {
    public static QueueEvent create(
            UUID queueId,
            UUID organizationId,
            String eventType,
            QueueStatus queueStatus,
            String currentTokenNumber,
            long waitingCount,
            UUID tokenId,
            String tokenNumber,
            TokenStatus tokenStatus
    ) {
        return new QueueEvent(
                queueId,
                organizationId,
                eventType,
                queueStatus,
                currentTokenNumber,
                waitingCount,
                tokenId,
                tokenNumber,
                tokenStatus,
                ZonedDateTime.now()
        );
    }
}
