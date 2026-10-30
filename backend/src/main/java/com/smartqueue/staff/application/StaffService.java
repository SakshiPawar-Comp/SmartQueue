package com.smartqueue.staff.application;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.counter.domain.CounterQueueAssignment;
import com.smartqueue.counter.domain.StaffCounterAssignment;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.counter.infrastructure.StaffCounterAssignmentRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.staff.api.dto.CreateStaffRequest;
import com.smartqueue.staff.api.dto.StaffResponse;
import com.smartqueue.staff.api.dto.UpdateStaffRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StaffService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final StaffCounterAssignmentRepository staffCounterAssignmentRepository;
    private final CounterQueueAssignmentRepository counterQueueAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            OrganizationRepository organizationRepository,
            BranchRepository branchRepository,
            StaffCounterAssignmentRepository staffCounterAssignmentRepository,
            CounterQueueAssignmentRepository counterQueueAssignmentRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.staffCounterAssignmentRepository = staffCounterAssignmentRepository;
        this.counterQueueAssignmentRepository = counterQueueAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(UUID organizationId, UUID branchId) {
        List<UserRole> staffRoles = userRoleRepository.findByOrganizationIdAndRoleCode(organizationId, "STAFF");
        List<StaffResponse> list = new ArrayList<>();

        for (UserRole ur : staffRoles) {
            User user = ur.getUser();
            Optional<StaffCounterAssignment> activeAssignment = staffCounterAssignmentRepository.findActiveByUserId(user.getId());
            if (branchId != null) {
                if (activeAssignment.isEmpty() || !activeAssignment.get().getBranch().getId().equals(branchId)) {
                    continue;
                }
            }
            list.add(toStaffResponse(user, organizationId, activeAssignment));
        }

        return list;
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaffById(UUID organizationId, UUID staffUserId) {
        User user = userRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(staffUserId);
        boolean isStaffInOrg = userRoles.stream().anyMatch(ur ->
                ur.getRole().getCode().equals("STAFF") &&
                ur.getOrganization() != null &&
                ur.getOrganization().getId().equals(organizationId));

        if (!isStaffInOrg) {
            throw new TenantViolationException("Staff user does not belong to your organization");
        }

        Optional<StaffCounterAssignment> activeAssignment = staffCounterAssignmentRepository.findActiveByUserId(staffUserId);
        return toStaffResponse(user, organizationId, activeAssignment);
    }

    public StaffResponse getStaffAssignments(UUID staffUserId) {
        User user = userRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(staffUserId);
        UUID orgId = userRoles.stream()
                .filter(ur -> ur.getOrganization() != null)
                .map(ur -> ur.getOrganization().getId())
                .findFirst()
                .orElse(null);

        Optional<StaffCounterAssignment> activeAssignment = staffCounterAssignmentRepository.findActiveByUserId(staffUserId);
        return toStaffResponse(user, orgId, activeAssignment);
    }

    @Transactional
    public StaffResponse createStaff(UUID organizationId, CreateStaffRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        if (userRepository.findByEmail(request.getEmail().trim().toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("User with email '" + request.getEmail() + "' already exists");
        }

        Role staffRole = roleRepository.findByCode("STAFF")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("STAFF");
                    r.setName("Staff");
                    return roleRepository.save(r);
                });

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
            if (!branch.getOrganization().getId().equals(organizationId)) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        user.setPreferredLanguage(request.getPreferredLanguage() != null ? request.getPreferredLanguage().trim() : "en");
        user.setStatus("ACTIVE");
        user.setCreatedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());
        user = userRepository.save(user);

        UserRole userRole = new UserRole(user, staffRole, organization);
        userRoleRepository.save(userRole);

        return toStaffResponse(user, organizationId, Optional.empty());
    }

    @Transactional
    public StaffResponse updateStaff(UUID organizationId, UUID staffUserId, UpdateStaffRequest request) {
        User user = userRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(staffUserId);
        boolean isStaffInOrg = userRoles.stream().anyMatch(ur ->
                ur.getRole().getCode().equals("STAFF") &&
                ur.getOrganization() != null &&
                ur.getOrganization().getId().equals(organizationId));

        if (!isStaffInOrg) {
            throw new TenantViolationException("Staff user does not belong to your organization");
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getPreferredLanguage() != null && !request.getPreferredLanguage().isBlank()) {
            user.setPreferredLanguage(request.getPreferredLanguage().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new IllegalArgumentException("Invalid status: must be ACTIVE or INACTIVE");
            }
            user.setStatus(status);
        }

        user.setUpdatedAt(ZonedDateTime.now());
        user = userRepository.save(user);

        Optional<StaffCounterAssignment> activeAssignment = staffCounterAssignmentRepository.findActiveByUserId(staffUserId);
        return toStaffResponse(user, organizationId, activeAssignment);
    }

    private StaffResponse toStaffResponse(User user, UUID organizationId, Optional<StaffCounterAssignment> activeAssignment) {
        StaffResponse response = new StaffResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setPreferredLanguage(user.getPreferredLanguage());
        response.setStatus(user.getStatus());
        response.setOrganizationId(organizationId);
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        if (activeAssignment.isPresent()) {
            StaffCounterAssignment sa = activeAssignment.get();
            response.setBranchId(sa.getBranch().getId());
            response.setBranchName(sa.getBranch().getName());
            response.setActiveCounterId(sa.getCounter().getId());
            response.setActiveCounterName(sa.getCounter().getName());

            List<CounterQueueAssignment> cqas = counterQueueAssignmentRepository.findByCounterId(sa.getCounter().getId());
            response.setAssignedQueueIds(cqas.stream().map(cqa -> cqa.getQueue().getId()).toList());
        } else {
            response.setAssignedQueueIds(List.of());
        }

        return response;
    }
}
