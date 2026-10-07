package com.billing.security;

import com.billing.model.AppUser;
import com.billing.model.Role;
import com.billing.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Tells services which tenant the signed-in user belongs to. */
@Component
@RequiredArgsConstructor
public class CurrentTenant {

    private final AppUserRepository users;

    /** Returns the tenant id of the signed-in tenant user, or throws (HTTP 403) for anyone else. */
    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Not signed in");
        }
        AppUser user = users.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
        if (user.getRole() != Role.TENANT || user.getTenantId() == null) {
            throw new AccessDeniedException("Only tenant users can use the billing data");
        }
        return user.getTenantId();
    }
}
