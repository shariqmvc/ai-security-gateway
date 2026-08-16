package com.ai.gateway.exception;

public class TenantAlreadyExistsException extends IllegalStateException {

    public TenantAlreadyExistsException(String tenantCode) {
        super("Tenant already exists: " + tenantCode);
    }
}