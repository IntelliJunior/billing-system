package com.billing.repository;

import com.billing.model.ModuleType;
import com.billing.model.Tenant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select t.enabled from Tenant t where t.id = :id")
    Optional<Boolean> findEnabledById(@Param("id") Long id);

    @Query("select t.name from Tenant t where t.id = :id")
    Optional<String> findNameById(@Param("id") Long id);

    boolean existsByInvoicePrefixIgnoreCase(String invoicePrefix);

    boolean existsByInvoicePrefixIgnoreCaseAndIdNot(String invoicePrefix, Long id);

    @Query("select t.stateCode from Tenant t where t.id = :id")
    Optional<String> findStateCodeById(@Param("id") Long id);

    /** Locks the tenant row, so invoice numbers of one tenant are handed out one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findForUpdate(@Param("id") Long id);

    @Query("select m from Tenant t join t.modules m where t.id = :id")
    List<ModuleType> findModulesById(@Param("id") Long id);
}
