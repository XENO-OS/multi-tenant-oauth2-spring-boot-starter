package com.xenotech.multitenant.context.resolvers;

import com.xenotech.multitenant.utils.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class OidcTenantResolver implements TenantResolver<HttpServletRequest> {

    public OidcTenantResolver() {
        //empty constructor
    }

    @Override
    public String resolveTenantIdentifier(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());
            try {
                String issuerUrl = TokenUtils.getJWTClaimSet(token).getIssuer();
                // Extract tenant ID (realm name) from issuer URL
                int lastSlashIndex = issuerUrl.lastIndexOf('/');
                if (lastSlashIndex > 0) {
                    return issuerUrl.substring(lastSlashIndex + 1);
                } else {
                    // Handle case where issuer URL doesn't follow expected format
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return "";
    }
}
