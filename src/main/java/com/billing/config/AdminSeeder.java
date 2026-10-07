package com.billing.config;

import com.billing.model.AppUser;
import com.billing.model.Role;
import com.billing.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from application.properties if no admin exists yet.
 * The admin must change this password at first login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No admin user exists and app.admin.password is empty. "
                    + "Set app.admin.username / app.admin.password in application.properties.");
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(adminUsername.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setMustChangePassword(true);
        users.save(admin);
        log.info("Created admin user '{}'. Log in and change the password.", admin.getUsername());
    }
}
