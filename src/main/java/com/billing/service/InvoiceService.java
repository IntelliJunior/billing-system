package com.billing.service;

import com.billing.dto.InvoiceItemRequest;
import com.billing.dto.InvoiceRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.*;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;

    public List<Invoice> getAll() {
        return invoiceRepository.findAll();
    }

    public List<Invoice> getByCustomer(Long customerId) {
        return invoiceRepository.findByCustomerId(customerId);
    }

    public List<Invoice> getByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatus(status);
    }

    public Invoice getById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
    }

    public Invoice create(InvoiceRequest req) {
        Customer customer = customerService.getById(req.getCustomerId());

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setIssueDate(req.getIssueDate() != null ? req.getIssueDate() : java.time.LocalDate.now());
        invoice.setDueDate(req.getDueDate());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (InvoiceItemRequest itemReq : req.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);

            BigDecimal unitPrice = itemReq.getUnitPrice();
            String description = itemReq.getDescription();
            Double taxPercent = itemReq.getTaxPercent() != null ? itemReq.getTaxPercent() : 0.0;

            if (itemReq.getProductId() != null) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product not found with id: " + itemReq.getProductId()));
                item.setProduct(product);
                if (description == null || description.isBlank()) description = product.getName();
                if (unitPrice == null) unitPrice = product.getUnitPrice();
                if (itemReq.getTaxPercent() == null) taxPercent = product.getTaxPercent();
            }

            item.setDescription(description);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setTaxPercent(taxPercent);

            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            BigDecimal lineTax = lineSubtotal.multiply(BigDecimal.valueOf(taxPercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax).setScale(2, RoundingMode.HALF_UP);
            item.setLineTotal(lineTotal);

            subtotal = subtotal.add(lineSubtotal);
            totalTax = totalTax.add(lineTax);

            invoice.getItems().add(item);
        }

        invoice.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxAmount(totalTax.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(subtotal.add(totalTax).setScale(2, RoundingMode.HALF_UP));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus(InvoiceStatus.UNPAID);

        return invoiceRepository.save(invoice);
    }

    public Invoice recordPayment(Long invoiceId, BigDecimal amount, Payment.PaymentMethod method, String note) {
        Invoice invoice = getById(invoiceId);

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BadRequestException("Cannot record payment on a cancelled invoice");
        }

        BigDecimal balanceDue = invoice.getBalanceDue();
        if (amount.compareTo(balanceDue) > 0) {
            throw new BadRequestException(
                    "Payment amount (" + amount + ") exceeds balance due (" + balanceDue + ")");
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod(method != null ? method : Payment.PaymentMethod.CASH);
        payment.setReferenceNote(note);
        invoice.getPayments().add(payment);

        BigDecimal newAmountPaid = invoice.getAmountPaid().add(amount).setScale(2, RoundingMode.HALF_UP);
        invoice.setAmountPaid(newAmountPaid);

        if (newAmountPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (newAmountPaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        return invoiceRepository.save(invoice);
    }

    public Invoice cancel(Long invoiceId) {
        Invoice invoice = getById(invoiceId);
        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Cannot cancel an invoice that already has payments recorded");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return invoiceRepository.save(invoice);
    }

    public void delete(Long id) {
        invoiceRepository.delete(getById(id));
    }

    private String generateInvoiceNumber() {
        String datePart = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String candidate = "INV-" + datePart + "-" + randomPart;
        while (invoiceRepository.existsByInvoiceNumber(candidate)) {
            randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = "INV-" + datePart + "-" + randomPart;
        }
        return candidate;
    }
}
