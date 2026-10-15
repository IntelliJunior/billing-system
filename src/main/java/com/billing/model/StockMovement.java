package com.billing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line of the stock history. quantityChange is negative for stock going out. */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_mov_tenant", columnList = "tenant_id"),
        @Index(name = "idx_stock_mov_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    @JsonIgnore
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Name at the time of the movement, so history stays readable if the product is renamed or deleted
    @Column(nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityChange;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityAfter;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    private Long supplierId;
    private String supplierName;

    private String reason;

    @Column(length = 500)
    private String note;

    // For example the invoice number when stock is reduced by an invoice
    private String reference;

    private String createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
