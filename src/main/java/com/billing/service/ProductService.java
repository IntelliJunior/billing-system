package com.billing.service;

import com.billing.dto.ProductRequest;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.Product;
import com.billing.repository.ProductRepository;
import com.billing.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CurrentTenant currentTenant;

    public List<Product> getAll() {
        return productRepository.findByTenantId(currentTenant.id());
    }

    public Product getById(Long id) {
        return productRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Product create(ProductRequest req) {
        Product product = new Product();
        product.setTenantId(currentTenant.id());
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setUnitPrice(req.getUnitPrice());
        product.setTaxPercent(req.getTaxPercent());
        return productRepository.save(product);
    }

    public Product update(Long id, ProductRequest req) {
        Product product = getById(id);
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setUnitPrice(req.getUnitPrice());
        product.setTaxPercent(req.getTaxPercent());
        return productRepository.save(product);
    }

    public void delete(Long id) {
        productRepository.delete(getById(id));
    }
}
