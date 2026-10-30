package com.smartqueue.auth.infrastructure;

import com.smartqueue.auth.domain.Role;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class RoleDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataInitializer.class);

    private final RoleRepository roleRepository;

    public RoleDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        seedRole("11111111-1111-1111-1111-111111111111", "SUPER_ADMIN", "Super Admin");
        seedRole("22222222-2222-2222-2222-222222222222", "ORGANIZATION_ADMIN", "Organization Admin");
        seedRole("33333333-3333-3333-3333-333333333333", "BRANCH_ADMIN", "Branch Admin");
        seedRole("44444444-4444-4444-4444-444444444444", "STAFF", "Staff");
        seedRole("55555555-5555-5555-5555-555555555555", "CUSTOMER", "Customer");
        log.info("[RoleDataInitializer] Roles seeded (dev profile)");
    }

    private void seedRole(String id, String code, String name) {
        if (roleRepository.findByCode(code).isEmpty()) {
            Role role = new Role();
            role.setId(UUID.fromString(id));
            role.setCode(code);
            role.setName(name);
            roleRepository.save(role);
            log.info("[RoleDataInitializer] Seeded role: {}", code);
        }
    }
}
