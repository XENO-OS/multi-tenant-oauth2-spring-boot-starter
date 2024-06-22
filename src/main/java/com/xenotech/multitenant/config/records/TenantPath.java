package com.xenotech.multitenant.config.records;

public record TenantPath(String path,
                         String name,
                         String enforcementMode
) {
}
