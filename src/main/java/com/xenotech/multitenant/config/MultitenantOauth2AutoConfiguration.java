package com.xenotech.multitenant.config;

import com.xenotech.multitenant.TenantContextFilter;
import com.xenotech.multitenant.config.records.TenantHttpProperties;
import com.xenotech.multitenant.context.resolvers.HttpHeaderTenantResolver;
import com.xenotech.multitenant.security.TenantAuthenticationManagerResolver;
import com.xenotech.multitenant.tenantdetails.PropertiesTenantDetailsService;
import com.xenotech.multitenant.tenantdetails.TenantDetailsService;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore({
        WebMvcAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@ConditionalOnClass({
        TenantAuthenticationManagerResolver.class
})
@EnableConfigurationProperties(com.xenotech.multitenant.config.MultitenantOauth2Properties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MultitenantOauth2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantDetailsService tenantDetailsService(MultitenantOauth2Properties properties) {
        return new PropertiesTenantDetailsService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantAuthenticationManagerResolver tenantAuthenticationManagerResolver(TenantDetailsService tenantDetailsService) {
        return new TenantAuthenticationManagerResolver(tenantDetailsService);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpHeaderTenantResolver httpHeaderTenantResolver(MultitenantOauth2Properties properties) {
        return new HttpHeaderTenantResolver(properties.http());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantContextFilter tenantContextFilter(HttpHeaderTenantResolver httpHeaderTenantResolver,
                                                   TenantDetailsService tenantDetailsService) {
        return new TenantContextFilter(httpHeaderTenantResolver, tenantDetailsService);
    }
}
