package org.specdriven.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecDrivenPlatformTest {

    @Test
    void platform_isAlwaysPresentFromBuild() {
        // SpecDriven always assembles a LealonePlatform; platform() must not return null
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            assertNotNull(sdk.platform());
        } finally {
            sdk.close();
        }
    }

    @Test
    void platform_exposesCheckHealthAfterBuild() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            PlatformHealth health = sdk.platform().checkHealth();
            assertNotNull(health);
            assertNotNull(health.overallStatus());
            assertFalse(health.subsystems().isEmpty());
        } finally {
            sdk.close();
        }
    }

    @Test
    void platform_exposesMetricsAfterBuild() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            PlatformMetrics metrics = sdk.platform().metrics();
            assertNotNull(metrics);
            assertTrue(metrics.snapshotAt() > 0);
        } finally {
            sdk.close();
        }
    }

    @Test
    void buildPlatform_returnsPlatformWithCheckHealth() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();
        platform.start();
        try {
            PlatformHealth health = platform.checkHealth();
            assertNotNull(health);
        } finally {
            platform.close();
        }
    }
}
