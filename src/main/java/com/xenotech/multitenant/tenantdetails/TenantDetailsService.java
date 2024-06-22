package com.xenotech.multitenant.tenantdetails;

import com.xenotech.multitenant.config.records.TenantDetails;

import java.util.List;

public interface TenantDetailsService {

    List<TenantDetails> loadAllTenants();

    TenantDetails loadTenantByIdentifier(String identifier);
}
