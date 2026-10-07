package com.billing.repository;

import com.billing.model.Invoice;
import com.billing.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByTenantId(Long tenantId);

    List<Invoice> findByTenantIdAndCustomerId(Long tenantId, Long customerId);

    List<Invoice> findByTenantIdAndStatus(Long tenantId, InvoiceStatus status);

    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);

    // Invoice numbers stay unique across the whole system
    boolean existsByInvoiceNumber(String invoiceNumber);
}
