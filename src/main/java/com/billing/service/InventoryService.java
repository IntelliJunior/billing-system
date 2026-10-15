package com.billing.service;

import com.billing.dto.StockMovementRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.MovementType;
import com.billing.model.Product;
import com.billing.model.StockMovement;
import com.billing.model.Supplier;
import com.billing.repository.ProductRepository;
import com.billing.repository.StockMovementRepository;
import com.billing.repository.SupplierRepository;
import com.billing.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private static final int HISTORY_LIMIT = 300;

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final SupplierRepository supplierRepository;
    private final CurrentTenant currentTenant;

    /** Products that have stock tracking switched on. */
    @Transactional(readOnly = true)
    public List<Product> stockList(boolean lowOnly) {
        return productRepository.findByTenantId(currentTenant.id()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getTrackStock()))
                .filter(p -> !lowOnly || isLow(p))
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movements(Long productId) {
        Long tenantId = currentTenant.id();
        Pageable latest = PageRequest.of(0, HISTORY_LIMIT);
        return productId == null
                ? movementRepository.findByTenantIdOrderByIdDesc(tenantId, latest)
                : movementRepository.findByTenantIdAndProductIdOrderByIdDesc(tenantId, productId, latest);
    }

    /** Stock In / Stock Out / Adjustment entered by the user. */
    public StockMovement recordMovement(StockMovementRequest req) {
        Long tenantId = currentTenant.id();
        // Lock the product row while its stock changes
        Product product = productRepository.findForUpdate(req.getProductId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + req.getProductId()));
        if (!Boolean.TRUE.equals(product.getTrackStock())) {
            throw new BadRequestException("Stock tracking is not switched on for \"" + product.getName() + "\"");
        }

        BigDecimal qty = req.getQuantity().setScale(3, RoundingMode.HALF_UP);
        BigDecimal current = nz(product.getQuantityOnHand());

        BigDecimal change = switch (req.getType()) {
            case IN -> {
                requirePositive(qty);
                yield qty;
            }
            case OUT -> {
                requirePositive(qty);
                if (qty.compareTo(current) > 0) {
                    throw new BadRequestException("Only " + plain(current) + " " + unitOf(product) + " in stock");
                }
                yield qty.negate();
            }
            case ADJUSTMENT -> {
                BigDecimal diff = qty.subtract(current);
                if (diff.signum() == 0) {
                    throw new BadRequestException("The counted quantity is the same as the current stock");
                }
                yield diff;
            }
        };

        Supplier supplier = null;
        if (req.getType() == MovementType.IN && req.getSupplierId() != null) {
            supplier = supplierRepository.findByIdAndTenantId(req.getSupplierId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        }
        if (req.getType() == MovementType.IN && req.getUnitCost() != null) {
            product.setCostPrice(req.getUnitCost());   // cost price follows the latest purchase price
        }

        return addMovement(product, req.getType(), change, req.getUnitCost(), supplier,
                clean(req.getReason()), clean(req.getNote()), null);
    }

    /** Used when a product is created with an opening stock. */
    public void addOpeningStock(Product product, BigDecimal quantity) {
        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        if (qty.signum() > 0) {
            addMovement(product, MovementType.IN, qty, product.getCostPrice(), null, "Opening stock", null, null);
        }
    }

    /** Changes the stock of a (locked or freshly created) product and writes the history line. */
    public StockMovement addMovement(Product product, MovementType type, BigDecimal change, BigDecimal unitCost,
                                     Supplier supplier, String reason, String note, String reference) {
        BigDecimal after = nz(product.getQuantityOnHand()).add(change).setScale(3, RoundingMode.HALF_UP);
        product.setQuantityOnHand(after);
        productRepository.save(product);

        StockMovement m = new StockMovement();
        m.setTenantId(product.getTenantId());
        m.setProductId(product.getId());
        m.setProductName(product.getName());
        m.setType(type);
        m.setQuantityChange(change);
        m.setQuantityAfter(after);
        m.setUnitCost(unitCost);
        if (supplier != null) {
            m.setSupplierId(supplier.getId());
            m.setSupplierName(supplier.getName());
        }
        m.setReason(reason);
        m.setNote(note);
        m.setReference(reference);
        m.setCreatedBy(currentUsername());
        return movementRepository.save(m);
    }

    // ------------------------------------------------------------ invoice link

    /**
     * Takes the sold quantities out of stock. Products without stock tracking are ignored.
     * If any tracked product is short, nothing is deducted and the invoice is rejected.
     */
    public void deductForInvoice(String invoiceNumber, Map<Long, BigDecimal> required) {
        Long tenantId = currentTenant.id();

        List<Long> ids = new ArrayList<>(required.keySet());
        Collections.sort(ids);   // always lock in the same order so two invoices cannot block each other

        Map<Long, Product> toDeduct = new LinkedHashMap<>();
        List<String> shortages = new ArrayList<>();
        for (Long id : ids) {
            Product p = productRepository.findForUpdate(id, tenantId).orElse(null);
            if (p == null || !Boolean.TRUE.equals(p.getTrackStock())) {
                continue;
            }
            BigDecimal need = required.get(id).setScale(3, RoundingMode.HALF_UP);
            BigDecimal have = nz(p.getQuantityOnHand());
            if (need.compareTo(have) > 0) {
                shortages.add(p.getName() + " (" + plain(have) + " " + unitOf(p) + " in stock, " + plain(need) + " needed)");
            } else {
                toDeduct.put(id, p);
            }
        }
        if (!shortages.isEmpty()) {
            throw new BadRequestException("Not enough stock: " + String.join("; ", shortages));
        }

        for (Map.Entry<Long, Product> e : toDeduct.entrySet()) {
            BigDecimal need = required.get(e.getKey()).setScale(3, RoundingMode.HALF_UP);
            addMovement(e.getValue(), MovementType.OUT, need.negate(), null, null,
                    "Invoice", null, invoiceNumber);
        }
    }

    /**
     * Puts back whatever this invoice still holds out of stock. Safe to call twice:
     * the second call finds nothing left to return.
     */
    public void restoreForInvoice(String invoiceNumber, String reason) {
        Long tenantId = currentTenant.id();

        Map<Long, BigDecimal> net = new TreeMap<>();
        for (StockMovement m : movementRepository.findByTenantIdAndReference(tenantId, invoiceNumber)) {
            net.merge(m.getProductId(), m.getQuantityChange(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : net.entrySet()) {
            if (e.getValue().signum() < 0) {
                Product p = productRepository.findForUpdate(e.getKey(), tenantId).orElse(null);
                if (p != null) {
                    addMovement(p, MovementType.IN, e.getValue().negate(), null, null, reason, null, invoiceNumber);
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean isLow(Product p) {
        return p.getReorderLevel() != null && nz(p.getQuantityOnHand()).compareTo(p.getReorderLevel()) <= 0;
    }

    private void requirePositive(BigDecimal qty) {
        if (qty.signum() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private String unitOf(Product p) {
        return (p.getUnit() == null || p.getUnit().isBlank()) ? "units" : p.getUnit();
    }

    private String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
