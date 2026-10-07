package com.billing.dto;

public record MeResponse(String username, String role, Long tenantId, String tenantName,
                         boolean mustChangePassword) {
}
