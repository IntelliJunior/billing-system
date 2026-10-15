package com.billing.repository;

import com.billing.model.InvoiceSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    Optional<InvoiceSequence> findByTenantIdAndFinancialYear(Long tenantId, String financialYear);
}
