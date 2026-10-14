package com.xenotech.multitenant.context;

import com.xenotech.multitenant.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsNullWhenNoTenantIsSet() {
        assertNull(TenantContextHolder.getTenantIdentifier());
    }

    @Test
    void returnsTheTenantThatWasSet() {
        TenantContextHolder.setTenantIdentifier("realm_1");

        assertEquals("realm_1", TenantContextHolder.getTenantIdentifier());
        assertEquals("realm_1", TenantContextHolder.getRequiredTenantIdentifier());
    }

    @Test
    void requiredTenantThrowsWhenNoTenantIsSet() {
        assertThrows(TenantNotFoundException.class, TenantContextHolder::getRequiredTenantIdentifier);
    }

    @Test
    void clearRemovesTheTenant() {
        TenantContextHolder.setTenantIdentifier("realm_1");

        TenantContextHolder.clear();

        assertNull(TenantContextHolder.getTenantIdentifier());
    }

    @Test
    void rejectsBlankTenant() {
        assertThrows(IllegalArgumentException.class, () -> TenantContextHolder.setTenantIdentifier(""));
        assertThrows(IllegalArgumentException.class, () -> TenantContextHolder.setTenantIdentifier("  "));
        assertThrows(IllegalArgumentException.class, () -> TenantContextHolder.setTenantIdentifier(null));
    }

    @Test
    void tenantIsIsolatedPerThread() throws InterruptedException {
        TenantContextHolder.setTenantIdentifier("realm_1");
        var seenByOtherThread = new AtomicReference<String>("unset");

        var thread = new Thread(() -> seenByOtherThread.set(TenantContextHolder.getTenantIdentifier()));
        thread.start();
        thread.join();

        assertNull(seenByOtherThread.get());
        assertEquals("realm_1", TenantContextHolder.getTenantIdentifier());
    }
}
