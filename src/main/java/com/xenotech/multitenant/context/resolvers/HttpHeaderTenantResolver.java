package com.xenotech.multitenant.context.resolvers;


import com.xenotech.multitenant.config.records.TenantHttpProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class HttpHeaderTenantResolver implements TenantResolver<HttpServletRequest> {

    private final TenantHttpProperties tenantHttpProperties;

    public HttpHeaderTenantResolver(TenantHttpProperties tenantHttpProperties) {
        this.tenantHttpProperties = tenantHttpProperties;
    }

    @Override
    @Nullable
    public String resolveTenantIdentifier(HttpServletRequest request) {
        String headerName = tenantHttpProperties.headerName();
        if(headerName==null) {
            headerName = "xeno_investors";
        }
        return request.getHeader(headerName);
    }
}
