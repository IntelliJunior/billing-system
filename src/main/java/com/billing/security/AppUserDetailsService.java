package com.billing.security;

import com.billing.model.AppUser;
import com.billing.model.Role;
import com.billing.repository.AppUserRepository;
import com.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;
    private final TenantRepository tenants;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = users.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        // A tenant user can log in only while the tenant is enabled
        boolean enabled = u.getRole() == Role.ADMIN
                || (u.getTenantId() != null && tenants.findEnabledById(u.getTenantId()).orElse(false));

        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole().name())
                .disabled(!enabled)
                .build();
    }
}
