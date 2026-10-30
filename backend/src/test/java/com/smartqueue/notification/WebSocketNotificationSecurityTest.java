package com.smartqueue.notification;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.infrastructure.websocket.WebSocketAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WebSocketNotificationSecurityTest {

    @Autowired
    private WebSocketAuthInterceptor authInterceptor;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization orgA;
    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        orgA = new Organization();
        orgA.setId(UUID.randomUUID());
        orgA.setName("Org A");
        orgA.setSlug("org-a-" + UUID.randomUUID().toString().substring(0, 8));
        orgA.setStatus("ACTIVE");
        orgA.setCreatedAt(ZonedDateTime.now());
        orgA.setUpdatedAt(ZonedDateTime.now());
        orgA = organizationRepository.save(orgA);

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Role not found"));

        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("userA@" + UUID.randomUUID() + ".com");
        userA.setPasswordHash("pass");
        userA.setFullName("User A");
        userA.setStatus("ACTIVE");
        userA.setCreatedAt(ZonedDateTime.now());
        userA.setUpdatedAt(ZonedDateTime.now());
        userA = userRepository.save(userA);
        userRoleRepository.save(new UserRole(userA, customerRole, orgA));

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userB@" + UUID.randomUUID() + ".com");
        userB.setPasswordHash("pass");
        userB.setFullName("User B");
        userB.setStatus("ACTIVE");
        userB.setCreatedAt(ZonedDateTime.now());
        userB.setUpdatedAt(ZonedDateTime.now());
        userB = userRepository.save(userB);
        userRoleRepository.save(new UserRole(userB, customerRole, orgA));

        tokenA = jwtProvider.generateAccessToken(userA.getEmail(), userA.getId().toString());
        tokenB = jwtProvider.generateAccessToken(userB.getEmail(), userB.getId().toString());
    }

    private Authentication authenticateConnect(String token) {
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> result = authInterceptor.preSend(connectMessage, null);
        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Authentication auth = (Authentication) resultAccessor.getUser();
        assertNotNull(auth, "Authenticated user principal must be set on connect");
        return auth;
    }

    @Test
    @DisplayName("WebSocket Notification Security: User A subscribing to own notifications topic succeeds")
    void testSubscribeOwnNotifications_succeeds() {
        Authentication authA = authenticateConnect(tokenA);

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/topic/users/" + userA.getId() + "/notifications");
        subAccessor.setUser(authA);
        subAccessor.setSubscriptionId("sub-user-a");
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(subMessage, null);
        assertNotNull(result, "Subscription to own notification topic should be accepted");
    }

    @Test
    @DisplayName("WebSocket Notification Security: User A subscribing to User B's notification topic is rejected")
    void testSubscribeCrossUserNotifications_rejected() {
        Authentication authA = authenticateConnect(tokenA);

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/topic/users/" + userB.getId() + "/notifications");
        subAccessor.setUser(authA);
        subAccessor.setSubscriptionId("sub-user-b-evil");
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> {
            authInterceptor.preSend(subMessage, null);
        }, "Subscription to another customer's notifications topic must be rejected with AccessDeniedException");
    }
}
