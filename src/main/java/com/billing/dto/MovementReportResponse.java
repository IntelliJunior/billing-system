package com.billing.dto;

import com.billing.model.StockMovement;

import java.time.LocalDate;
import java.util.List;

public record MovementReportResponse(
        LocalDate from,
        LocalDate to,
        int rowCount,
        boolean truncated,
        List<ProductMovementSummary> byProduct,
        List<StockMovement> rows) {
}
