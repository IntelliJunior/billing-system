package com.billing.controller;

import com.billing.dto.InvoiceRequest;
import com.billing.dto.PaymentRequest;
import com.billing.model.Invoice;
import com.billing.model.InvoiceStatus;
import com.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public List<Invoice> getAll(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) InvoiceStatus status) {
        if (customerId != null) return invoiceService.getByCustomer(customerId);
        if (status != null) return invoiceService.getByStatus(status);
        return invoiceService.getAll();
    }

    @GetMapping("/{id}")
    public Invoice getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice create(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(request);
    }

    @PostMapping("/{id}/payments")
    public Invoice recordPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return invoiceService.recordPayment(id, request.getAmount(), request.getMethod(), request.getReferenceNote());
    }

    @PostMapping("/{id}/cancel")
    public Invoice cancel(@PathVariable Long id) {
        return invoiceService.cancel(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        invoiceService.delete(id);
    }
}
