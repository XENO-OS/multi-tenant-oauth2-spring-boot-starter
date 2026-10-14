package com.xenotech.multitenant;

import com.xenotech.multitenant.config.MultitenantOauth2AutoConfiguration;
import com.xenotech.multitenant.config.MultitenantOauth2Properties;
import com.xenotech.multitenant.context.resolvers.OidcTenantResolver;
import com.xenotech.multitenant.security.TenantAuthenticationManagerResolver;
import com.xenotech.multitenant.tenantdetails.PropertiesTenantDetailsService;
import com.xenotech.multitenant.tenantdetails.TenantDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MultitenantOauth2AutoConfigurationTest {

    private static final String PREFIX = "spring.security.oauth2.resourceserver.multitenancy.tenants";

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MultitenantOauth2AutoConfiguration.class))
            .withPropertyValues(
                    PREFIX + "[0].identifier=realm_1",
                    PREFIX + "[0].enabled=true",
                    PREFIX + "[0].issuer=https://idp.example.com/realms/realm_1",
                    PREFIX + "[0].lazy-load-paths=true",
                    PREFIX + "[0].paths[0].path=/api/v1/public/*",
                    PREFIX + "[0].paths[0].name=public",
                    PREFIX + "[0].paths[0].enforcement-mode=DISABLED");

    @Test
    void registersTheDefaultBeansInAServletApplication() {
        webRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertNotNull(context.getBean(TenantDetailsService.class));
            assertInstanceOf(PropertiesTenantDetailsService.class, context.getBean(TenantDetailsService.class));
            assertNotNull(context.getBean(TenantAuthenticationManagerResolver.class));
            assertNotNull(context.getBean(OidcTenantResolver.class));
            assertNotNull(context.getBean(TenantContextFilter.class));
        });
    }

    @Test
    void bindsTenantPropertiesIncludingPaths() {
        webRunner.run(context -> {
            var tenants = context.getBean(MultitenantOauth2Properties.class).tenants();

            assertEquals(1, tenants.size());
            var tenant = tenants.getFirst();
            assertEquals("realm_1", tenant.identifier());
            assertTrue(tenant.enabled());
            assertTrue(tenant.lazyLoadPaths());
            assertEquals("https://idp.example.com/realms/realm_1", tenant.issuer());
            assertEquals("/api/v1/public/*", tenant.paths().getFirst().path());
            assertEquals("DISABLED", tenant.paths().getFirst().enforcementMode());
        });
    }

    @Test
    void backsOffWhenTheApplicationDefinesItsOwnTenantDetailsService() {
        webRunner.withUserConfiguration(CustomTenants.class).run(context -> {
            assertEquals(1, context.getBeansOfType(TenantDetailsService.class).size());
            assertSame(CustomTenants.SERVICE, context.getBean(TenantDetailsService.class));
        });
    }

    @Test
    void doesNotActivateOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultitenantOauth2AutoConfiguration.class))
                .run(context -> assertTrue(context.getBeansOfType(TenantDetailsService.class).isEmpty()));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTenants {

        static final TenantDetailsService SERVICE = new TenantDetailsService() {
            @Override
            public List<com.xenotech.multitenant.config.records.TenantDetails> loadAllTenants() {
                return List.of();
            }

            @Override
            public com.xenotech.multitenant.config.records.TenantDetails loadTenantByIdentifier(String identifier) {
                return null;
            }
        };

        @Bean
        TenantDetailsService tenantDetailsService() {
            return SERVICE;
        }
    }
}
