package com.smartqueue.queue.infrastructure.websocket;

import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.security.CustomUserDetailsService;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.queue.infrastructure.QueueRepository;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final QueueRepository queueRepository;

    public WebSocketAuthInterceptor(
            JwtProvider jwtProvider,
            CustomUserDetailsService customUserDetailsService,
            QueueRepository queueRepository) {
        this.jwtProvider = jwtProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.queueRepository = queueRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
            return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        String token = null;

        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                token = header.substring(7);
            } else if (StringUtils.hasText(header)) {
                token = header;
            }
        }

        if (!StringUtils.hasText(token)) {
            List<String> tokenHeaders = accessor.getNativeHeader("token");
            if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
                token = tokenHeaders.get(0);
            }
        }

        if (StringUtils.hasText(token)) {
            try {
                if (jwtProvider.validateToken(token)) {
                    String userIdStr = jwtProvider.getUserIdFromToken(token);
                    UUID userId = UUID.fromString(userIdStr);
                    UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    accessor.setUser(authentication);
                    log.debug("WebSocket authenticated user: {}", userDetails.getUsername());
                }
            } catch (Exception e) {
                log.warn("WebSocket authentication failed: {}", e.getMessage());
            }
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        // Topic 2: /topic/queues/{queueId} — Public queue status broadcasts (waiting count, current token)
        // Allow customers, visitors, and kiosk displays to subscribe without tenant conflict.
        if (destination.startsWith("/topic/queues/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                try {
                    UUID queueId = UUID.fromString(parts[3]);
                    var queue = queueRepository.findById(queueId);
                    if (queue == null) {
                        throw new AccessDeniedException("Queue not found: " + queueId);
                    }
                    return;
                } catch (IllegalArgumentException e) {
                    throw new AccessDeniedException("Invalid queue ID in subscription destination");
                }
            }
            return;
        }

        Principal userPrincipal = accessor.getUser();
        if (userPrincipal == null || !(userPrincipal instanceof Authentication)) {
            log.warn("Unauthenticated STOMP subscription attempt to {}", destination);
            throw new AccessDeniedException("Authentication required for destination: " + destination);
        }

        Authentication auth = (Authentication) userPrincipal;
        if (!(auth.getPrincipal() instanceof CustomUserDetails)) {
            throw new AccessDeniedException("Invalid principal for destination: " + destination);
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        boolean isSuperAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (isSuperAdmin) {
            return;
        }

        // Topic 1: /topic/orgs/{orgId}/queues or /topic/organizations/{orgId}/...
        if (destination.startsWith("/topic/orgs/") || destination.startsWith("/topic/organizations/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                try {
                    UUID targetOrgId = UUID.fromString(parts[3]);
                    boolean hasAccess = userDetails.getUserRoles().stream()
                            .anyMatch(ur -> ur.getOrganization() != null
                                    && ur.getOrganization().getId().equals(targetOrgId));
                    if (!hasAccess) {
                        log.warn("Tenant isolation violation: user {} attempted subscription to {}", userDetails.getUsername(), destination);
                        throw new AccessDeniedException("Access denied to organization topic: " + targetOrgId);
                    }
                } catch (IllegalArgumentException e) {
                    throw new AccessDeniedException("Invalid organization ID in subscription destination");
                }
            }
        }

        // Topic 3: /topic/users/{userId}/notifications
        if (destination.startsWith("/topic/users/")) {
            String[] parts = destination.split("/");
            if (parts.length >= 4) {
                try {
                    UUID targetUserId = UUID.fromString(parts[3]);
                    if (!userDetails.getUser().getId().equals(targetUserId)) {
                        log.warn("User notification topic isolation violation: user {} attempted subscription to {}",
                                userDetails.getUsername(), destination);
                        throw new AccessDeniedException("Access denied to user notification topic: " + targetUserId);
                    }
                } catch (IllegalArgumentException e) {
                    throw new AccessDeniedException("Invalid user ID in subscription destination");
                }
            }
        }
    }
}
