package com.billing.dto;

import com.billing.model.Product;
import com.billing.model.StockMovement;

import java.math.BigDecimal;
import java.util.List;

public record InventorySummaryResponse(
        int trackedProducts,
        int lowStockCount,
        int outOfStockCount,
        BigDecimal stockValueAtCost,
        BigDecimal stockValueAtPrice,
        List<Product> attentionItems,
        List<StockMovement> recentMovements) {
}
