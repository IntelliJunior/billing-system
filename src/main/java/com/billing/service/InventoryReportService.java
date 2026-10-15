package com.billing.service;

import com.billing.dto.InventorySummaryResponse;
import com.billing.dto.MovementReportResponse;
import com.billing.dto.ProductMovementSummary;
import com.billing.exception.BadRequestException;
import com.billing.model.MovementType;
import com.billing.model.Product;
import com.billing.model.StockMovement;
import com.billing.repository.StockMovementRepository;
import com.billing.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private static final int MAX_ROWS = 5000;
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryService inventoryService;
    private final StockMovementRepository movementRepository;
    private final CurrentTenant currentTenant;

    // ---------------------------------------------------------------- dashboard

    public InventorySummaryResponse summary() {
        List<Product> tracked = inventoryService.stockList(false);

        int low = 0;
        int out = 0;
        BigDecimal costValue = BigDecimal.ZERO;
        BigDecimal priceValue = BigDecimal.ZERO;
        List<Product> attention = new ArrayList<>();

        for (Product p : tracked) {
            BigDecimal qty = nz(p.getQuantityOnHand());
            costValue = costValue.add(qty.multiply(nz(p.getCostPrice())));
            priceValue = priceValue.add(qty.multiply(nz(p.getUnitPrice())));
            String status = statusOf(p);
            if (status.equals("OUT")) {
                out++;
                attention.add(p);
            } else if (status.equals("LOW")) {
                low++;
                attention.add(p);
            }
        }
        attention.sort(Comparator.comparing(p -> nz(p.getQuantityOnHand())));
        List<Product> topAttention = attention.size() > 10 ? new ArrayList<>(attention.subList(0, 10)) : attention;

        List<StockMovement> recent = movementRepository.findByTenantIdOrderByIdDesc(
                currentTenant.id(), PageRequest.of(0, 10));

        return new InventorySummaryResponse(tracked.size(), low, out, costValue, priceValue, topAttention, recent);
    }

    // ---------------------------------------------------------------- movement report

    public MovementReportResponse movementReport(LocalDate from, LocalDate to, Long productId, MovementType type) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new BadRequestException("The start date is after the end date");
        }

        List<StockMovement> filtered = movementRepository
                .findByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdDesc(
                        currentTenant.id(), start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream()
                .filter(m -> productId == null || productId.equals(m.getProductId()))
                .filter(m -> type == null || m.getType() == type)
                .toList();

        boolean truncated = filtered.size() > MAX_ROWS;
        List<StockMovement> rows = truncated ? filtered.subList(0, MAX_ROWS) : filtered;

        // Totals per product (quantities of different products are not added together)
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, BigDecimal> in = new LinkedHashMap<>();
        Map<Long, BigDecimal> outQty = new LinkedHashMap<>();
        for (StockMovement m : filtered) {
            names.putIfAbsent(m.getProductId(), m.getProductName());
            in.putIfAbsent(m.getProductId(), BigDecimal.ZERO);
            outQty.putIfAbsent(m.getProductId(), BigDecimal.ZERO);
            if (m.getQuantityChange().signum() > 0) {
                in.merge(m.getProductId(), m.getQuantityChange(), BigDecimal::add);
            } else {
                outQty.merge(m.getProductId(), m.getQuantityChange().negate(), BigDecimal::add);
            }
        }
        List<ProductMovementSummary> byProduct = names.entrySet().stream()
                .map(e -> new ProductMovementSummary(e.getKey(), e.getValue(),
                        in.get(e.getKey()), outQty.get(e.getKey()), in.get(e.getKey()).subtract(outQty.get(e.getKey()))))
                .sorted(Comparator.comparing(s -> s.productName().toLowerCase()))
                .toList();

        return new MovementReportResponse(start, end, filtered.size(), truncated, byProduct, new ArrayList<>(rows));
    }

    // ---------------------------------------------------------------- CSV exports

    public String stockCsv() {
        StringBuilder sb = new StringBuilder("\uFEFF");   // byte-order mark so Excel reads UTF-8
        sb.append("SKU,Product,Unit,On hand,Reorder level,Cost price,Stock value (cost),")
                .append("Selling price,Stock value (selling),Status\r\n");
        for (Product p : inventoryService.stockList(false)) {
            BigDecimal qty = nz(p.getQuantityOnHand());
            sb.append(text(p.getSku())).append(',')
                    .append(text(p.getName())).append(',')
                    .append(text(p.getUnit())).append(',')
                    .append(num(qty)).append(',')
                    .append(num(p.getReorderLevel())).append(',')
                    .append(num(p.getCostPrice())).append(',')
                    .append(num(qty.multiply(nz(p.getCostPrice())))).append(',')
                    .append(num(p.getUnitPrice())).append(',')
                    .append(num(qty.multiply(nz(p.getUnitPrice())))).append(',')
                    .append(statusOf(p)).append("\r\n");
        }
        return sb.toString();
    }

    public String movementsCsv(LocalDate from, LocalDate to, Long productId, MovementType type) {
        MovementReportResponse report = movementReport(from, to, productId, type);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Date,Product,Type,Change,Balance after,Reason,Note,Supplier,Unit cost,Reference,By\r\n");
        for (StockMovement m : report.rows()) {
            sb.append(m.getCreatedAt() == null ? "" : m.getCreatedAt().format(CSV_DATE)).append(',')
                    .append(text(m.getProductName())).append(',')
                    .append(m.getType()).append(',')
                    .append(num(m.getQuantityChange())).append(',')
                    .append(num(m.getQuantityAfter())).append(',')
                    .append(text(m.getReason())).append(',')
                    .append(text(m.getNote())).append(',')
                    .append(text(m.getSupplierName())).append(',')
                    .append(num(m.getUnitCost())).append(',')
                    .append(text(m.getReference())).append(',')
                    .append(text(m.getCreatedBy())).append("\r\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    private String statusOf(Product p) {
        BigDecimal qty = nz(p.getQuantityOnHand());
        if (qty.signum() <= 0) return "OUT";
        if (p.getReorderLevel() != null && qty.compareTo(p.getReorderLevel()) <= 0) return "LOW";
        return "OK";
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String num(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** Escapes a text cell and stops spreadsheet programs from running text such as "=1+1" as a formula. */
    private String text(String s) {
        if (s == null || s.isEmpty()) return "";
        String v = s;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
