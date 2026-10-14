package com.xenotech.multitenant;

import com.xenotech.multitenant.context.TenantContextHolder;
import com.xenotech.multitenant.security.TenantAuthenticationManagerResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the starter inside a real servlet container and exercises it over HTTP against a stub OIDC issuer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultitenantOauth2IntegrationTest {

    private static final StubIssuer ISSUER = StubIssuer.get();

    @DynamicPropertySource
    static void tenants(DynamicPropertyRegistry registry) {
        var prefix = "spring.security.oauth2.resourceserver.multitenancy.tenants";
        registry.add(prefix + "[0].identifier", () -> "realm_1");
        registry.add(prefix + "[0].enabled", () -> "true");
        registry.add(prefix + "[0].issuer", () -> ISSUER.issuerOf("realm_1"));
        registry.add(prefix + "[0].paths[0].path", () -> "/api/v1/public/*");
        registry.add(prefix + "[0].paths[0].name", () -> "public");
        registry.add(prefix + "[0].paths[0].enforcement-mode", () -> "DISABLED");
        registry.add(prefix + "[1].identifier", () -> "realm_2");
        registry.add(prefix + "[1].enabled", () -> "true");
        registry.add(prefix + "[1].issuer", () -> ISSUER.issuerOf("realm_2"));
        registry.add(prefix + "[2].identifier", () -> "realm_disabled");
        registry.add(prefix + "[2].enabled", () -> "false");
        registry.add(prefix + "[2].issuer", () -> ISSUER.issuerOf("realm_disabled"));
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void acceptsTokenFromFirstTenantAndExposesTenantContext() throws Exception {
        var response = get("/api/v1/whoami", ISSUER.token("realm_1", "alice"));

        assertEquals(200, response.statusCode());
        assertEquals("realm_1:alice", response.body());
    }

    @Test
    void acceptsTokenFromSecondTenantInTheSameApplication() throws Exception {
        var response = get("/api/v1/whoami", ISSUER.token("realm_2", "bob"));

        assertEquals(200, response.statusCode());
        assertEquals("realm_2:bob", response.body());
    }

    @Test
    void publicPathGetsItsConfiguredTenantWithoutAToken() throws Exception {
        var response = get("/api/v1/public/ping", null);

        assertEquals(200, response.statusCode());
        assertEquals("realm_1", response.body());
    }

    @Test
    void rejectsTokenSignedWithAnUnknownKey() throws Exception {
        var response = get("/api/v1/whoami", ISSUER.forgedToken("realm_1", "mallory"));

        assertEquals(401, response.statusCode());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        var response = get("/api/v1/whoami", ISSUER.expiredToken("realm_1", "alice"));

        assertEquals(401, response.statusCode());
    }

    @Test
    void rejectsTokenFromAnIssuerThatIsNotConfigured() throws Exception {
        var response = get("/api/v1/whoami", ISSUER.token("realm_unknown", "eve"));

        assertTrue(response.statusCode() >= 400, "status was " + response.statusCode());
    }

    @Test
    void rejectsTokenFromADisabledTenant() throws Exception {
        var response = get("/api/v1/whoami", ISSUER.token("realm_disabled", "alice"));

        assertTrue(response.statusCode() >= 400, "status was " + response.statusCode());
    }

    @Test
    void rejectsProtectedRequestWithoutATokenOrMatchingPublicPath() throws Exception {
        var response = get("/api/v1/whoami", null);

        assertTrue(response.statusCode() >= 400, "status was " + response.statusCode());
    }

    @Test
    void actuatorPathsDoNotRequireATenant() throws Exception {
        var response = get("/actuator/health", null);

        // no actuator is on the test classpath, so 404 proves the request got past the tenant filter
        assertEquals(404, response.statusCode());
    }

    @Test
    void tenantContextIsClearedAfterTheRequest() throws Exception {
        get("/api/v1/whoami", ISSUER.token("realm_1", "alice"));

        assertNull(TenantContextHolder.getTenantIdentifier());
    }

    private HttpResponse<String> get(String path, String bearerToken) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebSecurity
    @Import(Api.class)
    static class TestApplication {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                TenantAuthenticationManagerResolver resolver,
                                                TenantContextFilter tenantContextFilter) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/public/**", "/actuator/**", "/error").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(resolver))
                    .addFilterBefore(tenantContextFilter, BearerTokenAuthenticationFilter.class)
                    .build();
        }
    }

    @RestController
    static class Api {

        @GetMapping("/api/v1/whoami")
        String whoami(@AuthenticationPrincipal Jwt jwt) {
            return TenantContextHolder.getRequiredTenantIdentifier() + ":" + jwt.getSubject();
        }

        @GetMapping("/api/v1/public/ping")
        String ping() {
            return TenantContextHolder.getRequiredTenantIdentifier();
        }
    }
}
