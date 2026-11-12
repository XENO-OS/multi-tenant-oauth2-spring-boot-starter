package com.xenotech.multitenant;

import com.xenotech.multitenant.config.MultitenantOauth2Properties;
import com.xenotech.multitenant.config.MultitenantOauth2Properties.IssuerHttp;
import com.xenotech.multitenant.config.records.TenantDetails;
import com.xenotech.multitenant.context.TenantContextHolder;
import com.xenotech.multitenant.security.TenantAuthenticationManagerResolver;
import com.xenotech.multitenant.tenantdetails.PropertiesTenantDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that a tenant's JWT decoder reaches its issuer with the configured timeouts, against a stub issuer
 * whose responses take {@link StubIssuer#SLOW_RESPONSE_MILLIS} ms.
 *
 * <p>Each test uses its own tenant identifier because the resolver caches one authentication manager per
 * tenant for the life of the JVM.
 */
class IssuerTimeoutIntegrationTest {

    private static final StubIssuer ISSUER = StubIssuer.get();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void defaultTimeoutsTolerateAnIssuerSlowerThanSpringSecuritysDefault() {
        var resolver = resolverFor("slow_default", IssuerHttp.defaults());

        var authentication = authenticate(resolver, "slow_default");

        assertTrue(authentication.isAuthenticated());
        assertEquals("alice", ((Jwt) authentication.getPrincipal()).getSubject());
    }

    @Test
    void theSingleArgumentConstructorAlsoUsesTheDefaultTimeouts() {
        var tenants = new PropertiesTenantDetailsService(new MultitenantOauth2Properties(List.of(slowTenant("slow_scanned"))));
        var resolver = new TenantAuthenticationManagerResolver(tenants);

        assertTrue(authenticate(resolver, "slow_scanned").isAuthenticated());
    }

    /**
     * The first call to the issuer is discovery. Spring Security reports its failure as an
     * {@link IllegalArgumentException}; the resolver reports it as an {@link AuthenticationServiceException}, the
     * same type a JWK set failure has.
     */
    @Test
    void aReadTimeoutShorterThanTheIssuerFailsAsAnAuthenticationServiceException() {
        var resolver = resolverFor("slow_short", new IssuerHttp(Duration.ofSeconds(5), Duration.ofMillis(200)));

        var failure = assertThrows(AuthenticationServiceException.class, () -> authenticate(resolver, "slow_short"));
        assertTrue(causeChainHas(failure, SocketTimeoutException.class), () -> "expected a socket timeout, got: " + failure);
        assertTrue(failure.getMessage().contains("slow_short"));
    }

    @Test
    void anIssuerThatRefusesConnectionsFailsAsAnAuthenticationServiceException() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var issuer = "http://localhost:" + closedPort + "/realms/" + StubIssuer.SLOW_REALM;
        var tenant = new TenantDetails("refused", true, null, StubIssuer.SLOW_REALM, null, null, issuer, false, List.of());
        var properties = new MultitenantOauth2Properties(List.of(tenant));
        var resolver = new TenantAuthenticationManagerResolver(new PropertiesTenantDetailsService(properties));

        var failure = assertThrows(AuthenticationServiceException.class, () -> authenticate(resolver, "refused"));
        assertTrue(causeChainHas(failure, ConnectException.class), () -> "expected a refused connection, got: " + failure);
    }

    private static TenantAuthenticationManagerResolver resolverFor(String identifier, IssuerHttp issuerHttp) {
        var properties = new MultitenantOauth2Properties(List.of(slowTenant(identifier)), issuerHttp);
        return new TenantAuthenticationManagerResolver(new PropertiesTenantDetailsService(properties), issuerHttp);
    }

    private static TenantDetails slowTenant(String identifier) {
        return new TenantDetails(identifier, true, ISSUER.baseUrl(), StubIssuer.SLOW_REALM, null, null,
                ISSUER.issuerOf(StubIssuer.SLOW_REALM), false, List.of());
    }

    private static org.springframework.security.core.Authentication authenticate(
            TenantAuthenticationManagerResolver resolver, String identifier) {
        TenantContextHolder.setTenantIdentifier(identifier);
        var token = ISSUER.token(StubIssuer.SLOW_REALM, "alice");
        return resolver.resolve(null).authenticate(new BearerTokenAuthenticationToken(token));
    }

    private static boolean causeChainHas(Throwable failure, Class<? extends Throwable> type) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
