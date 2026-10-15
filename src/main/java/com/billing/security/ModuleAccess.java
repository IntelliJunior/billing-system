package com.billing.security;

import com.billing.model.ModuleType;
import com.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Checks which features the signed-in tenant has been given by the admin. */
@Component
@RequiredArgsConstructor
public class ModuleAccess {

    private final CurrentTenant currentTenant;
    private final TenantRepository tenants;

    public boolean has(ModuleType module) {
        return tenants.findModulesById(currentTenant.id()).contains(module);
    }

    public void require(ModuleType module) {
        if (!has(module)) {
            throw new AccessDeniedException("This feature is not enabled for your account");
        }
    }
}
