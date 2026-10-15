package com.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull
    @PositiveOrZero
    private BigDecimal unitPrice;

    @PositiveOrZero
    private Double taxPercent = 0.0;

    // HSN / SAC code: used only when the tenant has the GST feature
    private String hsnCode;

    // Inventory fields: used only when the tenant has the Inventory feature
    private String sku;
    private String unit;

    @PositiveOrZero(message = "Cost price cannot be negative")
    private BigDecimal costPrice;

    private Boolean trackStock;

    @PositiveOrZero(message = "Reorder level cannot be negative")
    private BigDecimal reorderLevel;

    // Only used when creating a product with stock tracking
    @PositiveOrZero(message = "Opening stock cannot be negative")
    private BigDecimal openingStock;
}
