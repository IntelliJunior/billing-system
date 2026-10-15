package com.billing.service;

import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.gst.GstReport;
import com.billing.gst.GstReportBuilder;
import com.billing.gst.GstReportBuilder.InvoiceIn;
import com.billing.gst.GstReportBuilder.LineIn;
import com.billing.gst.GstStates;
import com.billing.model.Invoice;
import com.billing.model.InvoiceItem;
import com.billing.model.InvoiceStatus;
import com.billing.model.Tenant;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.TenantRepository;
import com.billing.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GstReportService {

    private static final long MAX_DAYS = 1100;   // about three years
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final CurrentTenant currentTenant;
    private final GstSettingService settings;

    @Transactional(readOnly = true)
    public GstReport build(LocalDate from, LocalDate to, String label) {
        if (from == null || to == null) {
            throw new BadRequestException("Choose the start and end dates of the period");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("The start date is after the end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BadRequestException("Please choose a period of up to 3 years");
        }

        Long tenantId = currentTenant.id();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        List<InvoiceIn> invoices = new ArrayList<>();
        for (Invoice inv : invoiceRepository
                .findByTenantIdAndIssueDateBetweenOrderByIssueDateAscIdAsc(tenantId, from, to)) {
            invoices.add(toInput(inv));
        }

        GstReport.Header header = new GstReport.Header(
                tenant.getName(), tenant.getGstin(), GstStates.label(tenant.getStateCode()),
                from, to, cleanLabel(label, from, to), LocalDateTime.now());
        return GstReportBuilder.build(header, invoices, settings.b2clThreshold());
    }

    private InvoiceIn toInput(Invoice inv) {
        List<LineIn> lines = new ArrayList<>();
        for (InvoiceItem it : inv.getItems()) {
            lines.add(new LineIn(
                    it.getHsnCode(),
                    BigDecimal.valueOf(it.getTaxPercent() == null ? 0.0 : it.getTaxPercent()),
                    it.getQuantity(),
                    it.getTaxableValue(), it.getCgstAmount(), it.getSgstAmount(), it.getIgstAmount(),
                    it.getLineTotal()));
        }
        boolean cancelled = inv.getStatus() == InvoiceStatus.CANCELLED;
        return new InvoiceIn(
                inv.getInvoiceNumber(), inv.getIssueDate(), inv.getCustomer().getName(), inv.getCustomerGstin(),
                inv.getPlaceOfSupplyStateCode(), Boolean.TRUE.equals(inv.getInterState()),
                Boolean.TRUE.equals(inv.getGstInvoice()), cancelled, inv.getStatus().name(),
                inv.getSubtotal(), inv.getCgstAmount(), inv.getSgstAmount(), inv.getIgstAmount(),
                inv.getTaxAmount(), inv.getTotalAmount(), lines);
    }

    private String cleanLabel(String label, LocalDate from, LocalDate to) {
        String cleaned = label == null ? "" : label.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (cleaned.isEmpty()) {
            return from.format(LABEL_DATE) + " to " + to.format(LABEL_DATE);
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
