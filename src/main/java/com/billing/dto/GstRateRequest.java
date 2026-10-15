package com.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GstRateRequest {

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0", message = "Rate cannot be negative")
    @DecimalMax(value = "100", message = "Rate cannot be more than 100")
    private BigDecimal ratePercent;

    @Size(max = 100, message = "Label is too long")
    private String label;

    // Empty means no limit on that side
    private LocalDate validFrom;
    private LocalDate validTo;

    private Boolean active;
}
