package com.billing.service;

import com.billing.gst.GstCalculator;
import com.billing.model.InvoiceSequence;
import com.billing.model.Tenant;
import com.billing.repository.InvoiceSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Hands out GST invoice numbers such as IJ/26-27/0001: a short tenant prefix, the financial year
 * and a running number that starts again at 1 each April. At most 16 characters.
 * Call it inside the invoice transaction with the tenant row locked, so numbers never repeat.
 */
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private final InvoiceSequenceRepository sequences;

    public String next(Tenant tenant, LocalDate issueDate) {
        String financialYear = GstCalculator.financialYearLabel(issueDate);
        InvoiceSequence seq = sequences.findByTenantIdAndFinancialYear(tenant.getId(), financialYear)
                .orElseGet(() -> {
                    InvoiceSequence s = new InvoiceSequence();
                    s.setTenantId(tenant.getId());
                    s.setFinancialYear(financialYear);
                    s.setLastNumber(0L);
                    return s;
                });
        long number = seq.getLastNumber() + 1;
        seq.setLastNumber(number);
        sequences.save(seq);
        return tenant.getInvoicePrefix() + "/" + financialYear + "/" + String.format("%04d", number);
    }
}
