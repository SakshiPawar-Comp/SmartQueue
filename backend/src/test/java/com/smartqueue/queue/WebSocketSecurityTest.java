package com.smartqueue.queue;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.websocket.WebSocketAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
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
class WebSocketSecurityTest {

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

    @Autowired
    private QueueRepository queueRepository;

    private Organization orgA;
    private Organization orgB;
    private User userA;
    private User userB;
    private Queue queueA;
    private Queue queueB;
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

        orgB = new Organization();
        orgB.setId(UUID.randomUUID());
        orgB.setName("Org B");
        orgB.setSlug("org-b-" + UUID.randomUUID().toString().substring(0, 8));
        orgB.setStatus("ACTIVE");
        orgB.setCreatedAt(ZonedDateTime.now());
        orgB.setUpdatedAt(ZonedDateTime.now());
        orgB = organizationRepository.save(orgB);

        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setEmail("userA@" + UUID.randomUUID() + ".com");
        userA.setPasswordHash("pass");
        userA.setFullName("User A");
        userA.setStatus("ACTIVE");
        userA.setCreatedAt(ZonedDateTime.now());
        userA.setUpdatedAt(ZonedDateTime.now());
        userA = userRepository.save(userA);

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Role not found"));
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
        userRoleRepository.save(new UserRole(userB, customerRole, orgB));

        queueA = new Queue(orgA.getId(), "Queue A");
        queueA.setTokenPrefix("A");
        queueA = queueRepository.save(queueA);

        queueB = new Queue(orgB.getId(), "Queue B");
        queueB.setTokenPrefix("B");
        queueB = queueRepository.save(queueB);

        tokenA = jwtProvider.generateAccessToken(userA.getEmail(), userA.getId().toString());
        tokenB = jwtProvider.generateAccessToken(userB.getEmail(), userB.getId().toString());
    }

    @Test
    void connect_validJwt_setsUserAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + tokenA);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, null);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertTrue(resultAccessor.getUser() instanceof Authentication);
        assertEquals(userA.getEmail(), resultAccessor.getUser().getName());
    }

    @Test
    void connect_missingJwt_leavesUserNull() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, null);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }

    @Test
    void subscribe_unauthenticated_throwsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/orgs/" + orgA.getId() + "/queues");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, null));
    }

    @Test
    void subscribe_orgTopic_matchingTenant_allowed() {
        // First authenticate
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + tokenA);
        Message<?> connectMsg = authInterceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()), null);
        Authentication auth = (Authentication) StompHeaderAccessor.wrap(connectMsg).getUser();

        // Subscribe to Org A
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(auth);
        subAccessor.setDestination("/topic/orgs/" + orgA.getId() + "/queues");
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(subMsg, null);
        assertNotNull(result);
    }

    @Test
    void subscribe_orgTopic_differentTenant_throwsAccessDenied() {
        // Authenticate as User A (Org A)
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + tokenA);
        Message<?> connectMsg = authInterceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()), null);
        Authentication auth = (Authentication) StompHeaderAccessor.wrap(connectMsg).getUser();

        // Try to subscribe to Org B
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(auth);
        subAccessor.setDestination("/topic/orgs/" + orgB.getId() + "/queues");
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(subMsg, null));
    }

    @Test
    void subscribe_queueTopic_matchingTenant_allowed() {
        // Authenticate as User A (Org A)
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + tokenA);
        Message<?> connectMsg = authInterceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()), null);
        Authentication auth = (Authentication) StompHeaderAccessor.wrap(connectMsg).getUser();

        // Subscribe to Queue A
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(auth);
        subAccessor.setDestination("/topic/queues/" + queueA.getId());
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(subMsg, null);
        assertNotNull(result);
    }

    @Test
    void subscribe_queueTopic_differentTenant_throwsAccessDenied() {
        // Authenticate as User A (Org A)
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + tokenA);
        Message<?> connectMsg = authInterceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()), null);
        Authentication auth = (Authentication) StompHeaderAccessor.wrap(connectMsg).getUser();

        // Try to subscribe to Queue B (Org B)
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(auth);
        subAccessor.setDestination("/topic/queues/" + queueB.getId());
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(subMsg, null));
    }
}
