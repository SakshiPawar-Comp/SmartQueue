package com.smartqueue.queue.application;

import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.queue.api.dto.CustomerHistoryPageResponse;
import com.smartqueue.queue.api.dto.QueueTokenResponse;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final QueueTokenRepository queueTokenRepository;
    private final QueueService queueService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(
            QueueTokenRepository queueTokenRepository,
            QueueService queueService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.queueTokenRepository = queueTokenRepository;
        this.queueService = queueService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Get active tokens (WAITING, CALLED, HOLD) for the authenticated customer */
    public List<QueueTokenResponse> getActiveTokens(User customer) {
        List<QueueToken> tokens = queueTokenRepository.findActiveTokensByCustomerId(customer.getId());
        return tokens.stream().map(token -> {
            Integer position = null;
            Long peopleAhead = null;
            Integer estWait = null;
            ZonedDateTime expectedTurnTime = null;
            ZonedDateTime recommendedDepartureTime = null;

            if (token.getStatus() == TokenStatus.WAITING) {
                var estimate = queueService.getEstimate(token.getId());
                position = estimate.position();
                peopleAhead = (long) Math.max(0, estimate.position() - 1);
                estWait = estimate.estimatedWaitMinutes();
                expectedTurnTime = estimate.expectedTurnTime();
                recommendedDepartureTime = estimate.recommendedDepartureTime();
            } else if (token.getStatus() == TokenStatus.CALLED) {
                position = 1;
                peopleAhead = 0L;
                estWait = 0;
            } else if (token.getStatus() == TokenStatus.HOLD) {
                position = 1;
                peopleAhead = 0L;
                estWait = 0;
            }

            return QueueTokenResponse.fromEntity(token, position, peopleAhead, estWait, expectedTurnTime, recommendedDepartureTime);
        }).toList();
    }

    /** Get paginated token history for customer with optional status filter */
    public CustomerHistoryPageResponse getCustomerHistory(User customer, TokenStatus statusFilter, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 10;

        List<QueueToken> tokens = queueTokenRepository.findHistoryByCustomerId(customer.getId(), statusFilter, page, size);
        long total = queueTokenRepository.countHistoryByCustomerId(customer.getId(), statusFilter);
        int totalPages = (int) Math.ceil((double) total / size);

        List<QueueTokenResponse> items = tokens.stream().map(QueueTokenResponse::fromEntity).toList();
        return new CustomerHistoryPageResponse(items, total, totalPages, page, size);
    }

    /** Update customer profile fields (fullName, phone, preferredLanguage) */
    @Transactional
    public User updateProfile(User customer, String fullName, String phone, String preferredLanguage) {
        User user = userRepository.findById(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (phone != null) {
            user.setPhone(phone.trim());
        }
        if (preferredLanguage != null && !preferredLanguage.isBlank()) {
            user.setPreferredLanguage(preferredLanguage.trim());
        }
        user.setUpdatedAt(ZonedDateTime.now());
        return userRepository.save(user);
    }

    /** Secure password change for authenticated customer */
    @Transactional
    public void changePassword(User customer, String currentPassword, String newPassword) {
        User user = userRepository.findById(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(ZonedDateTime.now());
        userRepository.save(user);
    }
}
