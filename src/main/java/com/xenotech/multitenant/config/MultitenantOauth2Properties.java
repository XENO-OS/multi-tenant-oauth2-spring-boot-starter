package com.xenotech.multitenant.config;

import com.xenotech.multitenant.config.records.TenantDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configuration of the multi-tenant resource server, bound from
 * {@code spring.security.oauth2.resourceserver.multitenancy}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Carry the tenants the application accepts tokens from.</li>
 *   <li>Carry the HTTP timeouts used to reach each tenant's issuer for discovery and the JWK set.</li>
 * </ul>
 *
 * @param tenants    the tenants, one per issuer (realm)
 * @param issuerHttp timeouts for the calls made to a tenant's issuer; defaults apply when unset
 */
@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.multitenancy")
public record MultitenantOauth2Properties(List<TenantDetails> tenants, IssuerHttp issuerHttp) {

    /**
     * Binds both properties, applying the {@link IssuerHttp} defaults when the block is absent.
     *
     * @param tenants    the tenants, one per issuer (realm)
     * @param issuerHttp timeouts for the calls made to a tenant's issuer
     */
    @ConstructorBinding
    public MultitenantOauth2Properties(List<TenantDetails> tenants, @DefaultValue IssuerHttp issuerHttp) {
        this.tenants = tenants;
        this.issuerHttp = issuerHttp == null ? IssuerHttp.defaults() : issuerHttp;
    }

    /**
     * Creates properties for the given tenants with the default issuer timeouts.
     *
     * @param tenants the tenants, one per issuer (realm)
     */
    public MultitenantOauth2Properties(List<TenantDetails> tenants) {
        this(tenants, IssuerHttp.defaults());
    }

    /**
     * Timeouts for the HTTP calls a tenant's JWT decoder makes to its issuer: the OIDC discovery document on
     * first use, and the JWK set on first use and whenever it is refreshed.
     *
     * <p>Spring Security's own default for these calls is 500 ms (Nimbus's), short enough that an issuer a
     * few hundred milliseconds away fails intermittently. A failed call surfaces as an authentication error,
     * which an application that forwards it to {@code /error} can turn into a misleading 403.
     *
     * @param connectTimeout time allowed to open the connection; defaults to 5 seconds
     * @param readTimeout    time allowed to wait for the response; defaults to 5 seconds
     */
    public record IssuerHttp(@DefaultValue("5s") Duration connectTimeout, @DefaultValue("5s") Duration readTimeout) {

        /** The default for both timeouts. */
        public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

        /**
         * Validates both timeouts.
         *
         * @throws IllegalArgumentException if either timeout is null, zero or negative
         */
        public IssuerHttp {
            requirePositive(connectTimeout, "connect-timeout");
            requirePositive(readTimeout, "read-timeout");
        }

        /**
         * The defaults: 5 seconds for both timeouts.
         *
         * @return timeouts with both values at {@link #DEFAULT_TIMEOUT}
         */
        public static IssuerHttp defaults() {
            return new IssuerHttp(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("issuer-http." + name + " must be a positive duration, was " + value);
            }
        }
    }
}
