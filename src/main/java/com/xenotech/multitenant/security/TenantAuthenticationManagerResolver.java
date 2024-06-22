package com.xenotech.multitenant.security;

import com.xenotech.multitenant.context.TenantContextHolder;
import com.xenotech.multitenant.tenantdetails.TenantDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private static final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();
    private final TenantDetailsService tenantDetailsService;

    public TenantAuthenticationManagerResolver(TenantDetailsService tenantDetailsService) {
        this.tenantDetailsService = tenantDetailsService;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest context) {
        var tenantId = TenantContextHolder.getRequiredTenantIdentifier();
        return authenticationManagers.computeIfAbsent(tenantId, this::buildAuthenticationManager);
    }

    private AuthenticationManager buildAuthenticationManager(String tenantId) {
        var issuerUri = tenantDetailsService.loadTenantByIdentifier(tenantId).issuer();
        var jwtAuthenticationProvider = new JwtAuthenticationProvider(JwtDecoders.fromIssuerLocation(issuerUri));
        return jwtAuthenticationProvider::authenticate;
    }
}
