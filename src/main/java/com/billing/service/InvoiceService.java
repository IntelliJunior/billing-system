package com.billing.service;

import com.billing.dto.InvoiceItemRequest;
import com.billing.dto.InvoiceRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.gst.GstCalculator;
import com.billing.gst.GstStates;
import com.billing.gst.GstinValidator;
import com.billing.model.*;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.ProductRepository;
import com.billing.repository.TenantRepository;
import com.billing.security.CurrentTenant;
import com.billing.security.ModuleAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final CustomerService customerService;
    private final CurrentTenant currentTenant;
    private final ModuleAccess moduleAccess;
    private final InventoryService inventoryService;
    private final GstRateService gstRateService;
    private final InvoiceNumberService invoiceNumberService;

    public List<Invoice> getAll() {
        return invoiceRepository.findByTenantId(currentTenant.id());
    }

    public List<Invoice> getByCustomer(Long customerId) {
        return invoiceRepository.findByTenantIdAndCustomerId(currentTenant.id(), customerId);
    }

    public List<Invoice> getByStatus(InvoiceStatus status) {
        return invoiceRepository.findByTenantIdAndStatus(currentTenant.id(), status);
    }

    public Invoice getById(Long id) {
        return invoiceRepository.findByIdAndTenantId(id, currentTenant.id())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
    }

    public Invoice create(InvoiceRequest req) {
        Long tenantId = currentTenant.id();
        // Throws "not found" if the customer belongs to another tenant
        Customer customer = customerService.getById(req.getCustomerId());
        LocalDate issueDate = req.getIssueDate() != null ? req.getIssueDate() : LocalDate.now();

        // Tenants with the GST feature get GST invoices with numbered series
        boolean gst = moduleAccess.has(ModuleType.GST);
        Tenant tenant = null;
        if (gst) {
            // Lock the tenant row so two invoices cannot get the same number
            tenant = tenantRepository.findForUpdate(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            requireGstProfile(tenant);
        }

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomer(customer);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(req.getDueDate());
        invoice.setInvoiceNumber(gst ? invoiceNumberService.next(tenant, issueDate) : generateInvoiceNumber());

        boolean interState = false;
        if (gst) {
            String customerState = customer.getStateCode() != null
                    ? customer.getStateCode() : GstinValidator.stateCodeOf(customer.getGstin());
            String placeOfSupply = firstNonBlank(req.getPlaceOfSupplyStateCode(), customerState, tenant.getStateCode());
            if (!GstStates.isValid(placeOfSupply)) {
                throw new BadRequestException("Unknown place of supply");
            }
            interState = GstCalculator.isInterState(tenant.getStateCode(), placeOfSupply);

            invoice.setGstInvoice(true);
            invoice.setSupplierGstin(tenant.getGstin());
            invoice.setSupplierStateCode(tenant.getStateCode());
            invoice.setCustomerGstin(customer.getGstin());
            invoice.setCustomerStateCode(customerState);
            invoice.setPlaceOfSupplyStateCode(placeOfSupply);
            invoice.setInterState(interState);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        List<GstCalculator.LineResult> gstLines = new ArrayList<>();

        for (InvoiceItemRequest itemReq : req.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);

            BigDecimal unitPrice = itemReq.getUnitPrice();
            String description = itemReq.getDescription();
            Double taxPercent = itemReq.getTaxPercent() != null ? itemReq.getTaxPercent() : 0.0;
            String hsnCode = blankToNull(itemReq.getHsnCode());

            if (itemReq.getProductId() != null) {
                // Only products of this tenant can be used
                Product product = productRepository.findByIdAndTenantId(itemReq.getProductId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product not found with id: " + itemReq.getProductId()));
                item.setProduct(product);
                if (description == null || description.isBlank()) description = product.getName();
                if (unitPrice == null) unitPrice = product.getUnitPrice();
                if (itemReq.getTaxPercent() == null) taxPercent = product.getTaxPercent();
                if (hsnCode == null) hsnCode = product.getHsnCode();
            }

            item.setDescription(description);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setTaxPercent(taxPercent);

            if (gst) {
                BigDecimal rate = BigDecimal.valueOf(taxPercent);
                gstRateService.requireValid(rate, issueDate);
                if (hsnCode != null && !hsnCode.matches("\\d{4,8}")) {
                    throw new BadRequestException("HSN / SAC code must be 4 to 8 digits");
                }
                GstCalculator.LineResult r = GstCalculator.line(itemReq.getQuantity(), unitPrice, rate, interState);
                item.setHsnCode(hsnCode);
                item.setTaxableValue(r.taxable());
                item.setCgstAmount(r.cgst());
                item.setSgstAmount(r.sgst());
                item.setIgstAmount(r.igst());
                item.setLineTotal(r.total());
                gstLines.add(r);
            } else {
                BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                BigDecimal lineTax = lineSubtotal.multiply(BigDecimal.valueOf(taxPercent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                item.setLineTotal(lineSubtotal.add(lineTax).setScale(2, RoundingMode.HALF_UP));
                subtotal = subtotal.add(lineSubtotal);
                totalTax = totalTax.add(lineTax);
            }

            invoice.getItems().add(item);
        }

        if (gst) {
            GstCalculator.LineResult sum = GstCalculator.sum(gstLines);
            invoice.setSubtotal(sum.taxable());
            invoice.setTaxAmount(sum.tax());
            invoice.setTotalAmount(sum.total());
            invoice.setCgstAmount(sum.cgst());
            invoice.setSgstAmount(sum.sgst());
            invoice.setIgstAmount(sum.igst());
        } else {
            invoice.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
            invoice.setTaxAmount(totalTax.setScale(2, RoundingMode.HALF_UP));
            invoice.setTotalAmount(subtotal.add(totalTax).setScale(2, RoundingMode.HALF_UP));
        }
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus(InvoiceStatus.UNPAID);

        // Tenants with the Inventory feature: sold quantities leave stock (rejects the invoice if short)
        if (moduleAccess.has(ModuleType.INVENTORY)) {
            Map<Long, BigDecimal> required = new HashMap<>();
            for (InvoiceItem it : invoice.getItems()) {
                if (it.getProduct() != null) {
                    required.merge(it.getProduct().getId(), BigDecimal.valueOf(it.getQuantity()), BigDecimal::add);
                }
            }
            if (!required.isEmpty()) {
                inventoryService.deductForInvoice(invoice.getInvoiceNumber(), required);
            }
        }

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
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return invoice;   // already cancelled: stock was already put back
        }
        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Cannot cancel an invoice that already has payments recorded");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        Invoice saved = invoiceRepository.save(invoice);
        if (moduleAccess.has(ModuleType.INVENTORY)) {
            inventoryService.restoreForInvoice(saved.getInvoiceNumber(), "Invoice cancelled");
        }
        return saved;
    }

    public void delete(Long id) {
        Invoice invoice = getById(id);
        if (invoice.getStatus() != InvoiceStatus.CANCELLED && moduleAccess.has(ModuleType.INVENTORY)) {
            inventoryService.restoreForInvoice(invoice.getInvoiceNumber(), "Invoice deleted");
        }
        invoiceRepository.delete(invoice);
    }

    // ---------------------------------------------------------------- helpers

    private void requireGstProfile(Tenant tenant) {
        if (tenant.getGstin() == null || tenant.getStateCode() == null || tenant.getInvoicePrefix() == null) {
            throw new BadRequestException(
                    "GST details (GSTIN, state and invoice prefix) are missing for your account. Please contact your administrator.");
        }
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String generateInvoiceNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String candidate = "INV-" + datePart + "-" + randomPart;
        while (invoiceRepository.existsByInvoiceNumber(candidate)) {
            randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = "INV-" + datePart + "-" + randomPart;
        }
        return candidate;
    }
}
