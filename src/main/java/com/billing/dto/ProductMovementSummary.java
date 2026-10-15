package com.billing.dto;

import java.math.BigDecimal;

public record ProductMovementSummary(Long productId, String productName,
                                     BigDecimal quantityIn, BigDecimal quantityOut, BigDecimal netChange) {
}
