package com.billing.service;

import com.billing.dto.CustomerRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.Customer;
import com.billing.repository.CustomerRepository;
import com.billing.security.CurrentTenant;
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
        return customerRepository.save(customer);
    }

    public void delete(Long id) {
        Customer customer = getById(id);
        customerRepository.delete(customer);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
