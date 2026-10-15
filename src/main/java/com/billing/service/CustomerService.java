package com.billing.service;

import com.billing.dto.CustomerRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.gst.GstStates;
import com.billing.gst.GstinValidator;
import com.billing.model.Customer;
import com.billing.model.ModuleType;
import com.billing.repository.CustomerRepository;
import com.billing.security.CurrentTenant;
import com.billing.security.ModuleAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CurrentTenant currentTenant;
    private final ModuleAccess moduleAccess;

    public List<Customer> getAll() {
        return customerRepository.findByTenantId(currentTenant.id());
    }

    public Customer getById(Long id) {
        return customerRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    public Customer create(CustomerRequest req) {
        Long tenantId = currentTenant.id();
        String email = blankToNull(req.getEmail());
        if (email != null && customerRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
            throw new BadRequestException("A customer with this email already exists");
        }
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName(req.getName());
        customer.setEmail(email);
        customer.setPhone(req.getPhone());
        customer.setAddress(req.getAddress());
        applyGst(customer, req);
        return customerRepository.save(customer);
    }

    public Customer update(Long id, CustomerRequest req) {
        Customer customer = getById(id);
        String email = blankToNull(req.getEmail());
        if (email != null && customerRepository
                .existsByTenantIdAndEmailIgnoreCaseAndIdNot(customer.getTenantId(), email, id)) {
            throw new BadRequestException("A customer with this email already exists");
        }
        customer.setName(req.getName());
        customer.setEmail(email);
        customer.setPhone(req.getPhone());
        customer.setAddress(req.getAddress());
        applyGst(customer, req);
        return customerRepository.save(customer);
    }

    public void delete(Long id) {
        Customer customer = getById(id);
        customerRepository.delete(customer);
    }

    /** GSTIN and state are kept only for tenants with the GST feature. The state is taken from the GSTIN. */
    private void applyGst(Customer customer, CustomerRequest req) {
        if (!moduleAccess.has(ModuleType.GST)) {
            return;
        }
        String gstin = GstinValidator.normalize(blankToNull(req.getGstin()));
        String state = blankToNull(req.getStateCode());
        if (gstin != null) {
            if (!GstinValidator.isValid(gstin)) {
                throw new BadRequestException("The customer's GSTIN is not valid");
            }
            String gstinState = GstinValidator.stateCodeOf(gstin);
            if (state != null && !state.equals(gstinState)) {
                throw new BadRequestException("The state does not match the GSTIN, which starts with state code " + gstinState);
            }
            state = gstinState;
        }
        if (state != null && !GstStates.isValid(state)) {
            throw new BadRequestException("Unknown state code");
        }
        customer.setGstin(gstin);
        customer.setStateCode(state);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
