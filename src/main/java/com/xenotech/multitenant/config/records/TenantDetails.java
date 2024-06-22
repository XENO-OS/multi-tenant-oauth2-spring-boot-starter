package com.xenotech.multitenant.config.records;

import java.util.List;

public record TenantDetails (
        String identifier,
        boolean enabled,
        String serverUrl,
        String realm,
        String clientId,
        String clientSecret,
        String issuer,
        boolean lazyLoadPaths,
        List<TenantPath> paths
) {}
