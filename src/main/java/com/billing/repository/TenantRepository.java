package com.billing.repository;

import com.billing.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select t.enabled from Tenant t where t.id = :id")
    Optional<Boolean> findEnabledById(@Param("id") Long id);

    @Query("select t.name from Tenant t where t.id = :id")
    Optional<String> findNameById(@Param("id") Long id);
}
