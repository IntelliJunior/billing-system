package com.billing.repository;

import com.billing.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByTenantIdOrderByNameAsc(Long tenantId);

    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);
}
