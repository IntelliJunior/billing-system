package com.billing.controller;

import com.billing.dto.InventorySummaryResponse;
import com.billing.dto.MovementReportResponse;
import com.billing.model.MovementType;
import com.billing.service.InventoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryReportController {

    private final InventoryReportService reportService;

    @GetMapping("/summary")
    public InventorySummaryResponse summary() {
        return reportService.summary();
    }

    @GetMapping("/reports/movements")
    public MovementReportResponse movements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type) {
        return reportService.movementReport(from, to, productId, type);
    }

    @GetMapping("/reports/movements.csv")
    public ResponseEntity<byte[]> movementsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type) {
        return csv("stock-movements.csv", reportService.movementsCsv(from, to, productId, type));
    }

    @GetMapping("/reports/stock.csv")
    public ResponseEntity<byte[]> stockCsv() {
        return csv("stock-summary.csv", reportService.stockCsv());
    }

    private ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
