package com.xenotech.multitenant.tenantdetails;

import com.xenotech.multitenant.config.MultitenantOauth2Properties;
import com.xenotech.multitenant.config.records.TenantDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PropertiesTenantDetailsService implements TenantDetailsService {

    private final MultitenantOauth2Properties multitenantOauth2Properties;

    public PropertiesTenantDetailsService(MultitenantOauth2Properties multitenantOauth2Properties) {
        this.multitenantOauth2Properties = multitenantOauth2Properties;
    }

    @Override
    public List<TenantDetails> loadAllTenants() {
        return multitenantOauth2Properties.tenants();
    }

    @Override
    public TenantDetails loadTenantByIdentifier(String identifier) {
        return multitenantOauth2Properties.tenants().stream()
                .filter(TenantDetails::enabled)
                .filter(tenantDetails -> identifier.equals(tenantDetails.identifier()))
                .findFirst().orElse(null);
    }
}
