package com.billing.repository;

import com.billing.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByTenantId(Long tenantId);

    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndSkuIgnoreCase(Long tenantId, String sku);

    boolean existsByTenantIdAndSkuIgnoreCaseAndIdNot(Long tenantId, String sku, Long id);

    /** Locks the row so two stock changes on the same product cannot overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.tenantId = :tenantId")
    Optional<Product> findForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
