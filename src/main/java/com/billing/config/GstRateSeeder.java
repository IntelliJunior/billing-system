package com.billing.config;

import com.billing.model.GstRate;
import com.billing.model.GstSetting;
import com.billing.repository.GstRateRepository;
import com.billing.repository.GstSettingRepository;
import com.billing.service.GstSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fills the GST rate list and rule values the first time the app starts. After that the admin owns them. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GstRateSeeder implements ApplicationRunner {

    private final GstRateRepository rates;
    private final GstSettingRepository settings;

    @Override
    public void run(ApplicationArguments args) {
        if (rates.count() == 0) {
            LocalDate lastDayOfOldSlabs = LocalDate.of(2025, 9, 21);   // GST rates were restructured on 22 Sep 2025
            LocalDate firstDayOfNewSlab = LocalDate.of(2025, 9, 22);

            rates.saveAll(List.of(
                    new GstRate(new BigDecimal("0"), "0% (nil rated / exempt)", null, null),
                    new GstRate(new BigDecimal("0.25"), "0.25% (special rate)", null, null),
                    new GstRate(new BigDecimal("3"), "3% (special rate)", null, null),
                    new GstRate(new BigDecimal("5"), "5%", null, null),
                    new GstRate(new BigDecimal("12"), "12% (until 21 Sep 2025)", null, lastDayOfOldSlabs),
                    new GstRate(new BigDecimal("18"), "18% (standard)", null, null),
                    new GstRate(new BigDecimal("28"), "28% (until 21 Sep 2025)", null, lastDayOfOldSlabs),
                    new GstRate(new BigDecimal("40"), "40% (from 22 Sep 2025)", firstDayOfNewSlab, null)));
            log.info("Loaded the default GST rate list.");
        }
        if (settings.findBySettingKey(GstSettingService.B2CL_THRESHOLD).isEmpty()) {
            settings.save(new GstSetting(GstSettingService.B2CL_THRESHOLD,
                    GstSettingService.DEFAULT_B2CL_THRESHOLD.toPlainString(),
                    "Inter-state B2C invoices above this value are reported invoice by invoice"));
        }
    }
}
