package com.billing.service;

import com.billing.dto.ProductRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.ModuleType;
import com.billing.model.Product;
import com.billing.repository.ProductRepository;
import com.billing.security.CurrentTenant;
import com.billing.security.ModuleAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CurrentTenant currentTenant;
    private final ModuleAccess moduleAccess;
    private final InventoryService inventoryService;
    private final GstRateService gstRateService;

    public List<Product> getAll() {
        return productRepository.findByTenantId(currentTenant.id());
    }

    public Product getById(Long id) {
        return productRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Product create(ProductRequest req) {
        Long tenantId = currentTenant.id();
        boolean inventory = moduleAccess.has(ModuleType.INVENTORY);

        Product product = new Product();
        product.setTenantId(tenantId);
        applyBasics(product, req);
        applyGst(product, req);
        if (inventory) {
            applyInventoryFields(product, req, tenantId, null);
        }
        Product saved = productRepository.save(product);

        if (inventory && Boolean.TRUE.equals(saved.getTrackStock())
                && req.getOpeningStock() != null && req.getOpeningStock().signum() > 0) {
            inventoryService.addOpeningStock(saved, req.getOpeningStock());
        }
        return saved;
    }

    public Product update(Long id, ProductRequest req) {
        Product product = getById(id);
        applyBasics(product, req);
        applyGst(product, req);
        if (moduleAccess.has(ModuleType.INVENTORY)) {
            applyInventoryFields(product, req, product.getTenantId(), id);
        }
        return productRepository.save(product);
    }

    public void delete(Long id) {
        productRepository.delete(getById(id));
    }

    private void applyBasics(Product product, ProductRequest req) {
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setUnitPrice(req.getUnitPrice());
        product.setTaxPercent(req.getTaxPercent());
    }

    /** HSN/SAC code and a valid GST rate, for tenants with the GST feature. */
    private void applyGst(Product product, ProductRequest req) {
        if (!moduleAccess.has(ModuleType.GST)) {
            return;
        }
        String hsn = blankToNull(req.getHsnCode());
        if (hsn != null && !hsn.matches("\\d{4,8}")) {
            throw new BadRequestException("HSN / SAC code must be 4 to 8 digits");
        }
        product.setHsnCode(hsn);
        Double rate = req.getTaxPercent() == null ? 0.0 : req.getTaxPercent();
        gstRateService.requireValid(BigDecimal.valueOf(rate), LocalDate.now());
    }

    /** Stock quantity is never set here; it only changes through stock movements. */
    private void applyInventoryFields(Product product, ProductRequest req, Long tenantId, Long productId) {
        String sku = blankToNull(req.getSku());
        if (sku != null) {
            boolean taken = productId == null
                    ? productRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, sku)
                    : productRepository.existsByTenantIdAndSkuIgnoreCaseAndIdNot(tenantId, sku, productId);
            if (taken) {
                throw new BadRequestException("Another product already uses the code \"" + sku + "\"");
            }
        }
        product.setSku(sku);
        product.setUnit(blankToNull(req.getUnit()) == null ? "pcs" : req.getUnit().trim());
        product.setCostPrice(req.getCostPrice());
        product.setTrackStock(Boolean.TRUE.equals(req.getTrackStock()));
        product.setReorderLevel(req.getReorderLevel());
        if (product.getQuantityOnHand() == null) {
            product.setQuantityOnHand(BigDecimal.ZERO);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
