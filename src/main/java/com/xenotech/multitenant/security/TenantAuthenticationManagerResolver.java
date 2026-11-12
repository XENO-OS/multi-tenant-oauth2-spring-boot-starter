package com.xenotech.multitenant.security;

import com.xenotech.multitenant.config.MultitenantOauth2Properties.IssuerHttp;
import com.xenotech.multitenant.context.TenantContextHolder;
import com.xenotech.multitenant.tenantdetails.TenantDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@link AuthenticationManager} for the tenant of the current request.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Build one JWT-validating {@link AuthenticationManager} per tenant on first use, and cache it.</li>
 *   <li>Validate each token against its tenant's configured issuer (signature, expiry and {@code iss}).</li>
 *   <li>Reach the issuer for discovery and the JWK set with the configured {@link IssuerHttp} timeouts rather
 *       than Spring Security's 500 ms default.</li>
 *   <li>Report an issuer that cannot be reached as an {@link AuthenticationServiceException}, whichever call
 *       failed, so Spring Security treats it as a server-side failure rather than a bad token.</li>
 * </ul>
 *
 * <p>A tenant's decoder is built on the first request for that tenant, which fetches the issuer's discovery
 * document; the JWK set is fetched on the first token decoded and refreshed as needed. A failed build is not
 * cached, so it is retried on the next request.
 */
@Component
public class TenantAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private static final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();
    private final TenantDetailsService tenantDetailsService;
    private final RestOperations issuerClient;

    /**
     * Creates a resolver that reaches issuers with the default timeouts. This is the constructor component
     * scanning uses; the auto-configuration uses the other one, with the configured timeouts.
     *
     * @param tenantDetailsService source of each tenant's issuer
     */
    @Autowired
    public TenantAuthenticationManagerResolver(TenantDetailsService tenantDetailsService) {
        this(tenantDetailsService, IssuerHttp.defaults());
    }

    /**
     * Creates a resolver that reaches issuers with the given timeouts.
     *
     * @param tenantDetailsService source of each tenant's issuer
     * @param issuerHttp           connect and read timeouts for the discovery and JWK set calls
     */
    public TenantAuthenticationManagerResolver(TenantDetailsService tenantDetailsService, IssuerHttp issuerHttp) {
        this.tenantDetailsService = tenantDetailsService;
        this.issuerClient = issuerClient(issuerHttp);
    }

    /**
     * Returns the authentication manager for the tenant set on {@link TenantContextHolder}, building it on
     * first use.
     *
     * @param context the current request; not used, the tenant comes from {@link TenantContextHolder}
     * @return the tenant's authentication manager
     * @throws com.xenotech.multitenant.exception.TenantResolutionException if no tenant is set for the request
     * @throws AuthenticationServiceException if the tenant's issuer cannot be reached to build the decoder
     */
    @Override
    public AuthenticationManager resolve(HttpServletRequest context) {
        var tenantId = TenantContextHolder.getRequiredTenantIdentifier();
        return authenticationManagers.computeIfAbsent(tenantId, this::buildAuthenticationManager);
    }

    /**
     * Builds the tenant's authentication manager.
     *
     * <p>Building the decoder fetches the issuer's discovery document. Spring Security reports a failure there as
     * an {@link IllegalArgumentException}, which would escape the security filter as an unexpected error, while
     * it reports a later JWK set failure as an {@link AuthenticationServiceException}, which the bearer-token
     * filter rethrows as a server-side failure. Wrapping the first makes both behave the same way. Nothing is
     * cached when this throws, so the next request tries the issuer again.
     *
     * @throws AuthenticationServiceException if the issuer's discovery document cannot be fetched or is invalid
     */
    private AuthenticationManager buildAuthenticationManager(String tenantId) {
        var issuerUri = tenantDetailsService.loadTenantByIdentifier(tenantId).issuer();
        JwtDecoder decoder;
        try {
            decoder = jwtDecoder(issuerUri);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationServiceException(
                    "Unable to load the issuer configuration of tenant '" + tenantId + "'", e);
        }
        var jwtAuthenticationProvider = new JwtAuthenticationProvider(decoder);
        return jwtAuthenticationProvider::authenticate;
    }

    /**
     * What {@code JwtDecoders.fromIssuerLocation} builds, with this resolver's HTTP client in place of the
     * 500 ms default: the same lazy discovery, the same default validators plus the issuer check.
     */
    private JwtDecoder jwtDecoder(String issuerUri) {
        var decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).restOperations(issuerClient).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    private static RestOperations issuerClient(IssuerHttp issuerHttp) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(issuerHttp.connectTimeout());
        requestFactory.setReadTimeout(issuerHttp.readTimeout());
        return new RestTemplate(requestFactory);
    }
}
