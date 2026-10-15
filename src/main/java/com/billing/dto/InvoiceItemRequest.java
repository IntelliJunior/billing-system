package com.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvoiceItemRequest {
    // Optional: link to a catalog product. If omitted, description/unitPrice are used as-is.
    private Long productId;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull
    @PositiveOrZero
    private BigDecimal unitPrice;

    // The GST rate for tenants with the GST feature
    @PositiveOrZero
    private Double taxPercent = 0.0;

    // HSN / SAC code (GST tenants). Falls back to the product's code.
    private String hsnCode;
}
