package com.xenotech.multitenant;

import com.xenotech.multitenant.config.records.TenantDetails;
import com.xenotech.multitenant.config.records.TenantPath;
import com.xenotech.multitenant.context.TenantContextHolder;
import com.xenotech.multitenant.context.resolvers.OidcTenantResolver;
import com.xenotech.multitenant.exception.TenantNotFoundException;
import com.xenotech.multitenant.exception.TenantResolutionException;
import com.xenotech.multitenant.tenantdetails.TenantDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantContextFilterTest {

    private final OidcTenantResolver resolver = mock(OidcTenantResolver.class);
    private final TenantDetailsService tenants = mock(TenantDetailsService.class);
    private final TenantContextFilter filter = new TenantContextFilter(resolver, tenants);

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        var realm1 = tenant("realm_1", true, new TenantPath("/api/v1/public/*", "public", "DISABLED"));
        var realm2 = tenant("realm_2", false);
        when(tenants.loadAllTenants()).thenReturn(List.of(realm1, realm2));
        when(tenants.loadTenantByIdentifier("realm_1")).thenReturn(realm1);
        // disabled / unknown tenants are not returned by the real service
        when(tenants.loadTenantByIdentifier("realm_2")).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void setsTenantFromTokenForTheDurationOfTheRequestThenClearsIt() throws Exception {
        when(resolver.resolveTenantIdentifier(any())).thenReturn("realm_1");
        var seen = new AtomicReference<String>();

        filter.doFilter(request("/api/v1/things"), response,
                (req, res) -> seen.set(TenantContextHolder.getTenantIdentifier()));

        assertEquals("realm_1", seen.get());
        assertNull(TenantContextHolder.getTenantIdentifier());
    }

    @Test
    void fallsBackToConfiguredPathWhenNoTokenIsPresent() throws Exception {
        when(resolver.resolveTenantIdentifier(any())).thenReturn("");
        var seen = new AtomicReference<String>();

        filter.doFilter(request("/api/v1/public/info"), response,
                (req, res) -> seen.set(TenantContextHolder.getTenantIdentifier()));

        assertEquals("realm_1", seen.get());
    }

    @Test
    void fallsBackToConfiguredPathWhenTokenCannotBeResolved() throws Exception {
        when(resolver.resolveTenantIdentifier(any())).thenReturn(null);
        var seen = new AtomicReference<String>();

        filter.doFilter(request("/api/v1/public/info"), response,
                (req, res) -> seen.set(TenantContextHolder.getTenantIdentifier()));

        assertEquals("realm_1", seen.get());
    }

    @Test
    void rejectsRequestWhenNoTenantCanBeDetermined() {
        when(resolver.resolveTenantIdentifier(any())).thenReturn("");
        var chain = new MockFilterChain();

        assertThrows(TenantResolutionException.class,
                () -> filter.doFilter(request("/api/v1/private"), response, chain));
        assertNull(chain.getRequest(), "the chain must not be invoked");
    }

    @Test
    void rejectsDisabledTenant() {
        when(resolver.resolveTenantIdentifier(any())).thenReturn("realm_2");
        var chain = new MockFilterChain();

        assertThrows(TenantNotFoundException.class,
                () -> filter.doFilter(request("/api/v1/things"), response, chain));
        assertNull(chain.getRequest());
    }

    @Test
    void rejectsUnknownTenant() {
        when(resolver.resolveTenantIdentifier(any())).thenReturn("unknown");

        assertThrows(TenantNotFoundException.class,
                () -> filter.doFilter(request("/api/v1/things"), response, new MockFilterChain()));
    }

    @Test
    void clearsTenantEvenWhenTheChainFails() {
        when(resolver.resolveTenantIdentifier(any())).thenReturn("realm_1");
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request("/api/v1/things"), response, failing));
        assertNull(TenantContextHolder.getTenantIdentifier());
    }

    @Test
    void skipsActuatorEndpoints() throws Exception {
        var chain = new MockFilterChain();

        filter.doFilter(request("/actuator/health"), response, chain);

        assertNotNull(chain.getRequest(), "the chain must be invoked");
        verifyNoInteractions(resolver);
    }

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static TenantDetails tenant(String identifier, boolean enabled, TenantPath... paths) {
        return new TenantDetails(identifier, enabled, "https://idp.example.com", identifier, "client", "secret",
                "https://idp.example.com/realms/" + identifier, false, List.of(paths));
    }
}
