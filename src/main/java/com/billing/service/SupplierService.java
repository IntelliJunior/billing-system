package com.billing.service;

import com.billing.dto.SupplierRequest;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.Supplier;
import com.billing.repository.SupplierRepository;
import com.billing.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final CurrentTenant currentTenant;

    @Transactional(readOnly = true)
    public List<Supplier> getAll() {
        return supplierRepository.findByTenantIdOrderByNameAsc(currentTenant.id());
    }

    public Supplier create(SupplierRequest req) {
        Supplier s = new Supplier();
        s.setTenantId(currentTenant.id());
        apply(s, req);
        return supplierRepository.save(s);
    }

    public Supplier update(Long id, SupplierRequest req) {
        Supplier s = find(id);
        apply(s, req);
        return supplierRepository.save(s);
    }

    public void delete(Long id) {
        supplierRepository.delete(find(id));
    }

    private Supplier find(Long id) {
        return supplierRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + id));
    }

    private void apply(Supplier s, SupplierRequest req) {
        s.setName(req.getName().trim());
        s.setPhone(blankToNull(req.getPhone()));
        s.setEmail(blankToNull(req.getEmail()));
        s.setAddress(blankToNull(req.getAddress()));
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
