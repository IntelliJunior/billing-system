package com.billing.repository;

import com.billing.model.Invoice;
import com.billing.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCustomerId(Long customerId);
    List<Invoice> findByStatus(InvoiceStatus status);
    boolean existsByInvoiceNumber(String invoiceNumber);
}
