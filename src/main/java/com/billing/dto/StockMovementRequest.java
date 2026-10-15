package com.billing.dto;

import com.billing.model.MovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockMovementRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Type is required")
    private MovementType type;

    // IN / OUT: the quantity moved. ADJUSTMENT: the counted quantity now on the shelf.
    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity cannot be negative")
    private BigDecimal quantity;

    private Long supplierId;

    @PositiveOrZero(message = "Unit cost cannot be negative")
    private BigDecimal unitCost;

    @Size(max = 100, message = "Reason is too long")
    private String reason;

    @Size(max = 500, message = "Note is too long")
    private String note;
}
