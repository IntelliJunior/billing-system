package com.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GstSettingsRequest {

    @NotNull(message = "The limit is required")
    @PositiveOrZero(message = "The limit cannot be negative")
    private BigDecimal b2clThreshold;
}
