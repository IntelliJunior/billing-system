package com.billing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products", indexes = @Index(name = "idx_products_tenant", columnList = "tenant_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Column(nullable = false)
    private String name;

    private String description;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @PositiveOrZero
    @Column(nullable = false)
    private Double taxPercent = 0.0;

    // HSN (goods) or SAC (services) code, used by tenants with the GST feature
    @Column(length = 8)
    private String hsnCode;

    // ---- Inventory fields (used only by tenants with the Inventory feature) ----
    // Wrapper types and no NOT NULL so existing rows keep working.
    private String sku;

    private String unit;

    @Column(precision = 12, scale = 2)
    private BigDecimal costPrice;

    private Boolean trackStock;

    @Column(precision = 12, scale = 3)
    private BigDecimal reorderLevel;

    // Changed only through stock movements, never directly
    @Column(precision = 12, scale = 3)
    private BigDecimal quantityOnHand;

    // Owner of this record. Never sent to or accepted from the browser.
    @Column(name = "tenant_id")
    @JsonIgnore
    private Long tenantId;
}
