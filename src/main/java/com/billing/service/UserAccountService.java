package com.billing.service;

import com.billing.dto.MeResponse;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.AppUser;
import com.billing.repository.AppUserRepository;
import com.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MeResponse me(String username) {
        AppUser u = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String tenantName = u.getTenantId() == null ? null : tenants.findNameById(u.getTenantId()).orElse(null);
        return new MeResponse(u.getUsername(), u.getRole().name(), u.getTenantId(), tenantName,
                u.isMustChangePassword());
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AppUser u = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, u.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
    }
}
