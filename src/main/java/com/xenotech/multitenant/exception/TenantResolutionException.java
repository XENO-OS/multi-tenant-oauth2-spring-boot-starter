package com.xenotech.multitenant.exception;

public class TenantResolutionException extends IllegalStateException {

    public TenantResolutionException() {
        super("Error while trying to resolve the current tenant");
    }

    public TenantResolutionException(String message) {
        super(message);
    }
}
