package com.xenotech.multitenant;

import com.xenotech.multitenant.context.TenantContextHolder;
import com.xenotech.multitenant.context.resolvers.OidcTenantResolver;
import com.xenotech.multitenant.exception.TenantNotFoundException;
import com.xenotech.multitenant.exception.TenantResolutionException;
import com.xenotech.multitenant.tenantdetails.TenantDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final OidcTenantResolver oidcTenantResolver;
    private final TenantDetailsService tenantDetailsService;

    public TenantContextFilter(OidcTenantResolver oidcTenantResolver, TenantDetailsService tenantDetailsService) {
        this.oidcTenantResolver = oidcTenantResolver;
        this.tenantDetailsService = tenantDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var tenantIdentifier = oidcTenantResolver.resolveTenantIdentifier(request);

        if(StringUtils.hasText(tenantIdentifier)) {
            if(!isTenantValid(tenantIdentifier)) {
                throw new TenantNotFoundException("The specified tenant doesn't exist or it's not enabled");
            }
            TenantContextHolder.setTenantIdentifier(tenantIdentifier);
        }else {
            throw new TenantResolutionException("A tenant must be specified for requests to %s".formatted(request.getRequestURI()));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private boolean isTenantValid(String tenantIdentifier) {
        var tenantDetails = tenantDetailsService.loadTenantByIdentifier(tenantIdentifier);
        return tenantDetails != null && tenantDetails.enabled();
    }

    private void clear() {
        TenantContextHolder.clear();
    }
}
