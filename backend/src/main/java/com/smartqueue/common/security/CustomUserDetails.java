package com.smartqueue.common.security;

import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private final User user;
    private final List<UserRole> userRoles;

    public CustomUserDetails(User user, List<UserRole> userRoles) {
        this.user = user;
        this.userRoles = userRoles;
    }

    public User getUser() {
        return user;
    }

    public List<UserRole> getUserRoles() {
        return userRoles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        for (UserRole ur : userRoles) {
            String role = ur.getRole().getCode();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            if (ur.getOrganization() != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role + "_" + ur.getOrganization().getId()));
            }
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equals(user.getStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(user.getStatus());
    }
}
