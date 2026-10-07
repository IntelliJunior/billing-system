package com.billing.controller;

import com.billing.dto.TenantCredentials;
import com.billing.dto.TenantForm;
import com.billing.dto.TenantResponse;
import com.billing.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class AdminTenantController {

    private final TenantService tenantService;

    @GetMapping
    public List<TenantResponse> list() {
        return tenantService.list();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable Long id) {
        return tenantService.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TenantCredentials create(@Valid @ModelAttribute TenantForm form) {
        return tenantService.create(form);
    }

    // POST (not PUT) because multipart updates are parsed reliably only for POST
    @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenantResponse update(@PathVariable Long id, @Valid @ModelAttribute TenantForm form) {
        return tenantService.update(id, form);
    }

    @PostMapping("/{id}/enable")
    public TenantResponse enable(@PathVariable Long id) {
        return tenantService.setEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    public TenantResponse disable(@PathVariable Long id) {
        return tenantService.setEnabled(id, false);
    }

    @PostMapping("/{id}/reset-password")
    public TenantCredentials resetPassword(@PathVariable Long id) {
        return tenantService.resetPassword(id);
    }

    @GetMapping("/{id}/banner")
    public ResponseEntity<byte[]> banner(@PathVariable Long id) {
        TenantService.BannerData b = tenantService.getBanner(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(b.contentType()))
                .cacheControl(CacheControl.noCache())
                .body(b.bytes());
    }
}
