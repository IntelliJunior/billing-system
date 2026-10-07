package com.billing.repository;

import com.billing.model.AppUser;
import com.billing.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    List<AppUser> findByRole(Role role);

    Optional<AppUser> findFirstByTenantId(Long tenantId);
}
