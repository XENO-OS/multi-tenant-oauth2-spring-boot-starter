package com.xenotech.multitenant.context.resolvers;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OidcTenantResolverTest {

    private final OidcTenantResolver resolver = new OidcTenantResolver();

    @Test
    void resolvesRealmNameFromIssuerOfBearerToken() {
        var request = bearer(jwtWithIssuer("https://idp.example.com/realms/realm_1"));

        assertEquals("realm_1", resolver.resolveTenantIdentifier(request));
    }

    @Test
    void returnsEmptyStringWhenThereIsNoAuthorizationHeader() {
        assertEquals("", resolver.resolveTenantIdentifier(new MockHttpServletRequest()));
    }

    @Test
    void returnsEmptyStringForNonBearerAuthorization() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        assertEquals("", resolver.resolveTenantIdentifier(request));
    }

    @Test
    void returnsNullWhenTokenIsNotAJwt() {
        assertNull(resolver.resolveTenantIdentifier(bearer("not-a-jwt")));
    }

    @Test
    void returnsNullWhenTokenHasNoIssuer() {
        var token = new PlainJWT(new JWTClaimsSet.Builder().subject("user").build()).serialize();

        assertNull(resolver.resolveTenantIdentifier(bearer(token)));
    }

    @Test
    void returnsNullWhenIssuerHasNoPath() {
        assertNull(resolver.resolveTenantIdentifier(bearer(jwtWithIssuer("issuer-without-slash"))));
    }

    private static MockHttpServletRequest bearer(String token) {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String jwtWithIssuer(String issuer) {
        return new PlainJWT(new JWTClaimsSet.Builder().issuer(issuer).subject("user").build()).serialize();
    }
}
