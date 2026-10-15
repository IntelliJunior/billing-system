package com.billing.service;

import com.billing.dto.GstRateRequest;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.model.GstRate;
import com.billing.repository.GstRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GstRateService {

    private final GstRateRepository rates;

    /** Rates tenants can pick from (switched-on rates only). */
    public List<GstRate> all() {
        return rates.findByActiveTrueOrderByRatePercentAsc();
    }

    /** Every rate, including switched-off ones, for the admin page. */
    public List<GstRate> allForAdmin() {
        return rates.findAllByOrderByRatePercentAscValidFromAsc();
    }

    /** Rejects a rate that is not on the rate list for the given invoice date. */
    public void requireValid(BigDecimal rate, LocalDate date) {
        boolean ok = rates.findByActiveTrueOrderByRatePercentAsc().stream().anyMatch(r ->
                r.getRatePercent().compareTo(rate) == 0
                        && (r.getValidFrom() == null || !date.isBefore(r.getValidFrom()))
                        && (r.getValidTo() == null || !date.isAfter(r.getValidTo())));
        if (!ok) {
            throw new BadRequestException("GST rate " + rate.stripTrailingZeros().toPlainString()
                    + "% cannot be used on an invoice dated " + date);
        }
    }

    @Transactional
    public GstRate create(GstRateRequest req) {
        GstRate rate = new GstRate();
        apply(rate, req);
        return rates.save(rate);
    }

    @Transactional
    public GstRate update(Long id, GstRateRequest req) {
        GstRate rate = find(id);
        apply(rate, req);
        return rates.save(rate);
    }

    @Transactional
    public GstRate setActive(Long id, boolean active) {
        GstRate rate = find(id);
        rate.setActive(active);
        return rates.save(rate);
    }

    private GstRate find(Long id) {
        return rates.findById(id).orElseThrow(() -> new ResourceNotFoundException("GST rate not found with id: " + id));
    }

    private void apply(GstRate rate, GstRateRequest req) {
        if (req.getValidFrom() != null && req.getValidTo() != null && req.getValidFrom().isAfter(req.getValidTo())) {
            throw new BadRequestException("\"Valid from\" is after \"valid to\"");
        }
        rate.setRatePercent(req.getRatePercent().setScale(2, java.math.RoundingMode.HALF_UP));
        rate.setLabel(req.getLabel() == null || req.getLabel().isBlank() ? null : req.getLabel().trim());
        rate.setValidFrom(req.getValidFrom());
        rate.setValidTo(req.getValidTo());
        if (req.getActive() != null) {
            rate.setActive(req.getActive());
        }
    }
}
