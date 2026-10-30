package com.smartqueue.queue.infrastructure.websocket;

import com.smartqueue.queue.domain.event.QueueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QueueEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(QueueEventPublisher.class);
    private final SimpMessagingTemplate messagingTemplate;

    public QueueEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleQueueEvent(QueueEvent event) {
        log.info("Broadcasting committed queue event [{}]: queueId={}, status={}, currentToken={}, waiting={}",
                event.eventType(), event.queueId(), event.queueStatus(), event.currentTokenNumber(), event.waitingCount());

        // Broadcast to general queue topic
        messagingTemplate.convertAndSend("/topic/queues/" + event.queueId(), event);

        // Broadcast to tenant-scoped queue topic
        if (event.organizationId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/organizations/" + event.organizationId() + "/queues/" + event.queueId(),
                    event
            );
        }
    }
}
