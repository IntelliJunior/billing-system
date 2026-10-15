package com.billing.config;

import com.billing.model.ModuleType;
import com.billing.model.Tenant;
import com.billing.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tenants created before the feature switches existed get Invoicing, as they had before. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantModuleMigration implements ApplicationRunner {

    private final TenantRepository tenants;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Tenant t : tenants.findAll()) {
            if (t.getModules().isEmpty()) {
                t.getModules().add(ModuleType.INVOICING);
                tenants.save(t);
                log.info("Tenant '{}' had no features set; enabled Invoicing.", t.getName());
            }
        }
    }
}
