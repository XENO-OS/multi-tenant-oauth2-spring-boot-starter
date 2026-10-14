package com.xenotech.multitenant.tenantdetails;

import com.xenotech.multitenant.config.MultitenantOauth2Properties;
import com.xenotech.multitenant.config.records.TenantDetails;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTenantDetailsServiceTest {

    private final TenantDetails enabled = tenant("realm_1", true);
    private final TenantDetails disabled = tenant("realm_2", false);
    private final PropertiesTenantDetailsService service =
            new PropertiesTenantDetailsService(new MultitenantOauth2Properties(List.of(enabled, disabled)));

    @Test
    void loadAllTenantsReturnsEveryConfiguredTenantIncludingDisabled() {
        assertEquals(List.of(enabled, disabled), service.loadAllTenants());
    }

    @Test
    void loadTenantByIdentifierReturnsMatchingEnabledTenant() {
        assertEquals(enabled, service.loadTenantByIdentifier("realm_1"));
    }

    @Test
    void loadTenantByIdentifierIgnoresDisabledTenants() {
        assertNull(service.loadTenantByIdentifier("realm_2"));
    }

    @Test
    void loadTenantByIdentifierReturnsNullForUnknownTenant() {
        assertNull(service.loadTenantByIdentifier("nope"));
    }

    @Test
    void loadTenantByIdentifierIsCaseSensitive() {
        assertNull(service.loadTenantByIdentifier("REALM_1"));
    }

    static TenantDetails tenant(String identifier, boolean enabled) {
        return new TenantDetails(identifier, enabled, "https://idp.example.com", identifier, "client", "secret",
                "https://idp.example.com/realms/" + identifier, false, List.of());
    }
}
