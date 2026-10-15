package com.billing.dto;

import com.billing.model.FilingFrequency;
import com.billing.model.ModuleType;

import java.time.LocalDateTime;
import java.util.List;

public record TenantResponse(
        Long id,
        String name,
        String contactName,
        String mobile,
        String email,
        String address,
        boolean enabled,
        boolean hasBanner,
        String accountHolder,
        String bankName,
        String accountNumber,
        String ifsc,
        String branch,
        String upiId,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ModuleType> modules,
        String gstin,
        String stateCode,
        FilingFrequency filingFrequency,
        String invoicePrefix) {
}
