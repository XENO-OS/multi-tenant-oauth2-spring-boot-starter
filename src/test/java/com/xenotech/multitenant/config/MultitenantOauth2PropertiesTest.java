package com.xenotech.multitenant.config;

import com.xenotech.multitenant.config.MultitenantOauth2Properties.IssuerHttp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultitenantOauth2PropertiesTest {

    @Test
    void theTenantsOnlyConstructorUsesTheDefaultIssuerTimeouts() {
        var properties = new MultitenantOauth2Properties(List.of());

        assertEquals(IssuerHttp.defaults(), properties.issuerHttp());
        assertEquals(Duration.ofSeconds(5), properties.issuerHttp().connectTimeout());
        assertEquals(Duration.ofSeconds(5), properties.issuerHttp().readTimeout());
    }

    @Test
    void aMissingIssuerHttpBlockFallsBackToTheDefaults() {
        assertEquals(IssuerHttp.defaults(), new MultitenantOauth2Properties(List.of(), null).issuerHttp());
    }

    @Test
    void keepsConfiguredIssuerTimeouts() {
        var issuerHttp = new IssuerHttp(Duration.ofSeconds(2), Duration.ofMillis(1500));

        assertSame(issuerHttp, new MultitenantOauth2Properties(List.of(), issuerHttp).issuerHttp());
    }

    @Test
    void rejectsANullZeroOrNegativeTimeout() {
        var fiveSeconds = Duration.ofSeconds(5);

        assertThrows(IllegalArgumentException.class, () -> new IssuerHttp(null, fiveSeconds));
        assertThrows(IllegalArgumentException.class, () -> new IssuerHttp(fiveSeconds, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new IssuerHttp(Duration.ofMillis(-1), fiveSeconds));
    }
}
