package com.billing.repository;

import com.billing.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenantId(Long tenantId);

    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndEmailIgnoreCase(Long tenantId, String email);

    boolean existsByTenantIdAndEmailIgnoreCaseAndIdNot(Long tenantId, String email, Long id);
}
