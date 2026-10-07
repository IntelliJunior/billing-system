package com.billing.dto;

import java.time.LocalDateTime;

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
        LocalDateTime updatedAt) {
}
