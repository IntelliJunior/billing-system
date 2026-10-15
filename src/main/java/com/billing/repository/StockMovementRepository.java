package com.billing.repository;

import com.billing.model.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    List<StockMovement> findByTenantIdAndProductIdOrderByIdDesc(Long tenantId, Long productId, Pageable pageable);

    /** All stock lines created for one invoice (used to put stock back exactly once). */
    List<StockMovement> findByTenantIdAndReference(Long tenantId, String reference);

    /** From is inclusive, to is exclusive. */
    List<StockMovement> findByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdDesc(
            Long tenantId, LocalDateTime from, LocalDateTime to);
}
