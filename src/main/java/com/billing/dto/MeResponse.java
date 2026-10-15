package com.billing.dto;

import com.billing.model.ModuleType;

import java.util.List;

public record MeResponse(String username, String role, Long tenantId, String tenantName,
                         boolean mustChangePassword, List<ModuleType> modules, String tenantStateCode) {
}
