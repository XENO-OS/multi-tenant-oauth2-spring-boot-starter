package com.xenotech.multitenant.config;

import com.xenotech.multitenant.config.records.TenantDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.multitenancy")
public record MultitenantOauth2Properties(List<TenantDetails> tenants) {
}
