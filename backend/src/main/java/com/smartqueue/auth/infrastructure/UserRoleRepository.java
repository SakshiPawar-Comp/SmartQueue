package com.smartqueue.auth.infrastructure;

import com.smartqueue.auth.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    @EntityGraph(attributePaths = {"role", "organization", "user"})
    List<UserRole> findByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Query("SELECT ur FROM UserRole ur JOIN FETCH ur.user JOIN FETCH ur.role WHERE ur.organization.id = :organizationId AND ur.role.code = :roleCode")
    List<UserRole> findByOrganizationIdAndRoleCode(@org.springframework.data.repository.query.Param("organizationId") UUID organizationId, @org.springframework.data.repository.query.Param("roleCode") String roleCode);
}
