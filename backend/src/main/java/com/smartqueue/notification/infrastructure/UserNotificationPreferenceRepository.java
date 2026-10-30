package com.smartqueue.notification.infrastructure;

import com.smartqueue.notification.domain.UserNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {

    Optional<UserNotificationPreference> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
