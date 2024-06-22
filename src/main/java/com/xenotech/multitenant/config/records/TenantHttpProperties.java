package com.xenotech.multitenant.config.records;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record TenantHttpProperties(@DefaultValue("X-TenantId") String headerName) {}
