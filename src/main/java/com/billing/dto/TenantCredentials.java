package com.billing.dto;

/** Returned once when a tenant is created or its password is reset. */
public record TenantCredentials(TenantResponse tenant, String username, String defaultPassword) {
}
