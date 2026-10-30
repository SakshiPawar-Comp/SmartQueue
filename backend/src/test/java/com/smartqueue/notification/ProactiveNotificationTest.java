package com.smartqueue.notification;

import com.smartqueue.auth.domain.User;
import com.smartqueue.notification.application.NotificationEventListener;
import com.smartqueue.notification.application.NotificationService;
import com.smartqueue.notification.application.NotificationTemplateService;
import com.smartqueue.notification.domain.NotificationEventType;
import com.smartqueue.notification.domain.UserNotificationPreference;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.application.QueueService.QueueStatusEstimate;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.domain.event.QueueEvent;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProactiveNotificationTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationTemplateService templateService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private QueueRepository queueRepository;
    @Mock private QueueTokenRepository queueTokenRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private QueueService queueService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    private UUID queueId = UUID.randomUUID();
    private UUID orgId = UUID.randomUUID();
    private Queue queue;
    private Organization org;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        queue = new Queue(orgId, "Test Queue");
        queue.setId(queueId);
        
        org = new Organization();
        org.setId(orgId);
        
        when(queueRepository.findById(queueId)).thenReturn(queue);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
    }

    @Test
    public void test5PersonThresholdAndIdempotency() {
        QueueToken token = new QueueToken(queue, "A1", new User());
        token.setId(UUID.randomUUID());
        token.setStatus(TokenStatus.WAITING);
        token.getCustomer().setId(UUID.randomUUID());

        when(queueTokenRepository.findByQueueId(queueId)).thenReturn(List.of(token));
        when(notificationService.getOrCreatePreferences(any())).thenReturn(new UserNotificationPreference(token.getCustomer()));

        // Case 1: 6 tokens ahead (position 7). Should NOT trigger.
        when(queueService.getEstimate(token.getId())).thenReturn(
            new QueueStatusEstimate(7, 30, ZonedDateTime.now().plusMinutes(30), null, null)
        );
        
        QueueEvent event = QueueEvent.create(queueId, orgId, "TOKEN_CALLED", null, "A0", 7L, UUID.randomUUID(), "A0", null);
        notificationEventListener.onQueueEvent(event);
        verify(notificationService, never()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_5), any(), any(), any(), any());

        // Case 2: 5 tokens ahead (position 6). Should trigger.
        when(queueService.getEstimate(token.getId())).thenReturn(
            new QueueStatusEstimate(6, 25, ZonedDateTime.now().plusMinutes(25), null, null)
        );
        when(notificationRepository.existsByTokenIdAndEventType(token.getId(), NotificationEventType.APPROACHING_TURN_5)).thenReturn(false);
        
        // mock render
        when(templateService.render(any(), any(), any())).thenReturn(new NotificationTemplateService.RenderedTemplate("T", "M"));

        notificationEventListener.onQueueEvent(event);
        verify(notificationService, atLeastOnce()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_5), any(), any(), any(), any());

        // Case 3: Idempotency (already notified)
        when(notificationRepository.existsByTokenIdAndEventType(token.getId(), NotificationEventType.APPROACHING_TURN_5)).thenReturn(true);
        reset(notificationService); // reset call count
        when(notificationService.getOrCreatePreferences(any())).thenReturn(new UserNotificationPreference(token.getCustomer()));

        notificationEventListener.onQueueEvent(event);
        verify(notificationService, never()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_5), any(), any(), any(), any());
    }

    @Test
    public void test3PersonThreshold() {
        QueueToken token = new QueueToken(queue, "A2", new User());
        token.setId(UUID.randomUUID());
        token.setStatus(TokenStatus.WAITING);
        token.getCustomer().setId(UUID.randomUUID());

        when(queueTokenRepository.findByQueueId(queueId)).thenReturn(List.of(token));
        when(notificationService.getOrCreatePreferences(any())).thenReturn(new UserNotificationPreference(token.getCustomer()));

        // 4 tokens ahead (position 5). No trigger for 3.
        when(queueService.getEstimate(token.getId())).thenReturn(
            new QueueStatusEstimate(5, 20, ZonedDateTime.now().plusMinutes(20), null, null)
        );
        QueueEvent event = QueueEvent.create(queueId, orgId, "TOKEN_CALLED", null, "A0", 5L, UUID.randomUUID(), "A0", null);
        notificationEventListener.onQueueEvent(event);
        verify(notificationService, never()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_3), any(), any(), any(), any());

        // 3 tokens ahead (position 4). Trigger.
        when(queueService.getEstimate(token.getId())).thenReturn(
            new QueueStatusEstimate(4, 15, ZonedDateTime.now().plusMinutes(15), null, null)
        );
        when(notificationRepository.existsByTokenIdAndEventType(token.getId(), NotificationEventType.APPROACHING_TURN_3)).thenReturn(false);
        when(templateService.render(any(), any(), any())).thenReturn(new NotificationTemplateService.RenderedTemplate("T", "M"));
        
        notificationEventListener.onQueueEvent(event);
        verify(notificationService, atLeastOnce()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_3), any(), any(), any(), any());
    }

    @Test
    public void testJumps6To3And7To2() {
        QueueToken token = new QueueToken(queue, "A3", new User());
        token.setId(UUID.randomUUID());
        token.setStatus(TokenStatus.WAITING);
        token.getCustomer().setId(UUID.randomUUID());

        when(queueTokenRepository.findByQueueId(queueId)).thenReturn(List.of(token));
        when(notificationService.getOrCreatePreferences(any())).thenReturn(new UserNotificationPreference(token.getCustomer()));
        when(templateService.render(any(), any(), any())).thenReturn(new NotificationTemplateService.RenderedTemplate("T", "M"));

        // Jump 6 -> 3
        when(queueService.getEstimate(token.getId())).thenReturn(
            new QueueStatusEstimate(4, 10, ZonedDateTime.now().plusMinutes(10), null, null)
        );
        QueueEvent event = QueueEvent.create(queueId, orgId, "TOKEN_CALLED", null, "A0", 4L, UUID.randomUUID(), "A0", null);
        notificationEventListener.onQueueEvent(event);
        
        verify(notificationService, atLeastOnce()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_5), any(), any(), any(), any());
        verify(notificationService, atLeastOnce()).createAndDispatch(any(Organization.class), any(User.class), any(QueueToken.class), any(Queue.class), any(com.smartqueue.notification.domain.NotificationChannel.class), eq(NotificationEventType.APPROACHING_TURN_3), any(), any(), any(), any());
    }

    @Test
    public void testTerminalStates() {
        QueueToken token = new QueueToken(queue, "A4", new User());
        token.setId(UUID.randomUUID());
        token.getCustomer().setId(UUID.randomUUID());

        when(queueTokenRepository.findByQueueId(queueId)).thenReturn(List.of(token));
        when(notificationService.getOrCreatePreferences(any())).thenReturn(new UserNotificationPreference(token.getCustomer()));
        when(templateService.render(any(), any(), any())).thenReturn(new NotificationTemplateService.RenderedTemplate("T", "M"));
        when(queueService.getEstimate(token.getId())).thenReturn(
            new QueueStatusEstimate(4, 10, ZonedDateTime.now().plusMinutes(10), null, null)
        );
        QueueEvent event = QueueEvent.create(queueId, orgId, "TOKEN_CALLED", null, "A0", 4L, UUID.randomUUID(), "A0", null);

        // CANCELLED
        token.setStatus(TokenStatus.CANCELLED);
        notificationEventListener.onQueueEvent(event);

        // SKIPPED
        token.setStatus(TokenStatus.SKIPPED);
        notificationEventListener.onQueueEvent(event);

        // COMPLETED
        token.setStatus(TokenStatus.COMPLETED);
        notificationEventListener.onQueueEvent(event);
        
        // HOLD
        token.setStatus(TokenStatus.HOLD);
        notificationEventListener.onQueueEvent(event);

        verify(notificationService, never()).createAndDispatch(any(), any(), any(), any(), any(), eq(NotificationEventType.APPROACHING_TURN_5), any(), any(), any(), any());
    }
}
