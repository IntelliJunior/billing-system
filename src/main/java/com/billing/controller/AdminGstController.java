package com.billing.controller;

import com.billing.dto.GstRateRequest;
import com.billing.dto.GstSettingsRequest;
import com.billing.dto.GstSettingsResponse;
import com.billing.model.GstRate;
import com.billing.service.GstRateService;
import com.billing.service.GstSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The admin's GST rules: the rate list and the report settings. */
@RestController
@RequestMapping("/api/admin/gst")
@RequiredArgsConstructor
public class AdminGstController {

    private final GstRateService rateService;
    private final GstSettingService settingService;

    @GetMapping("/rates")
    public List<GstRate> rates() {
        return rateService.allForAdmin();
    }

    @PostMapping("/rates")
    @ResponseStatus(HttpStatus.CREATED)
    public GstRate create(@Valid @RequestBody GstRateRequest request) {
        return rateService.create(request);
    }

    @PutMapping("/rates/{id}")
    public GstRate update(@PathVariable Long id, @Valid @RequestBody GstRateRequest request) {
        return rateService.update(id, request);
    }

    @PostMapping("/rates/{id}/activate")
    public GstRate activate(@PathVariable Long id) {
        return rateService.setActive(id, true);
    }

    @PostMapping("/rates/{id}/deactivate")
    public GstRate deactivate(@PathVariable Long id) {
        return rateService.setActive(id, false);
    }

    @GetMapping("/settings")
    public GstSettingsResponse settings() {
        return new GstSettingsResponse(settingService.b2clThreshold());
    }

    @PutMapping("/settings")
    public GstSettingsResponse updateSettings(@Valid @RequestBody GstSettingsRequest request) {
        return new GstSettingsResponse(settingService.updateB2clThreshold(request.getB2clThreshold()));
    }
}
