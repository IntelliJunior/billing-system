package com.billing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A GST rate and the dates between which it may be used on invoices. Editable by the admin. */
@Entity
@Table(name = "gst_rates")
@Getter
@Setter
@NoArgsConstructor
public class GstRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    private String label;

    // Null means "no start limit" / "no end limit"
    private LocalDate validFrom;
    private LocalDate validTo;

    @Column(nullable = false)
    private boolean active = true;

    public GstRate(BigDecimal ratePercent, String label, LocalDate validFrom, LocalDate validTo) {
        this.ratePercent = ratePercent;
        this.label = label;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }
}
