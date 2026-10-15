package com.billing.service;

import com.billing.model.GstSetting;
import com.billing.repository.GstSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GstSettingService {

    public static final String B2CL_THRESHOLD = "B2CL_THRESHOLD";
    public static final BigDecimal DEFAULT_B2CL_THRESHOLD = new BigDecimal("100000");

    private final GstSettingRepository settings;

    /** Inter-state B2C invoices above this value are listed one by one in the GST report. */
    public BigDecimal b2clThreshold() {
        return settings.findBySettingKey(B2CL_THRESHOLD)
                .map(s -> {
                    try {
                        return new BigDecimal(s.getSettingValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_B2CL_THRESHOLD;
                    }
                })
                .orElse(DEFAULT_B2CL_THRESHOLD);
    }

    @Transactional
    public BigDecimal updateB2clThreshold(BigDecimal value) {
        GstSetting s = settings.findBySettingKey(B2CL_THRESHOLD)
                .orElseGet(() -> new GstSetting(B2CL_THRESHOLD, DEFAULT_B2CL_THRESHOLD.toPlainString(),
                        "Inter-state B2C invoices above this value are reported invoice by invoice"));
        s.setSettingValue(value.toPlainString());
        settings.save(s);
        return value;
    }
}
