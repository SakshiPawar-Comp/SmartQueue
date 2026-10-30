package com.smartqueue.notification.infrastructure;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt, n.updatedAt = :readAt WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") UUID userId, @Param("readAt") ZonedDateTime readAt);

    List<Notification> findByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(NotificationStatus status, int maxAttempts);

    boolean existsByTokenIdAndEventType(UUID tokenId, com.smartqueue.notification.domain.NotificationEventType eventType);
}
