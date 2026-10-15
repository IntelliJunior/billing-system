package com.billing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Last invoice number used by a tenant in a financial year (numbers run 1, 2, 3 without gaps). */
@Entity
@Table(name = "invoice_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uq_invoice_seq", columnNames = {"tenant_id", "financial_year"}))
@Getter
@Setter
@NoArgsConstructor
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Column(nullable = false)
    private long lastNumber;
}
