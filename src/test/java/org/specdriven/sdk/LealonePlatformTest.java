package org.specdriven.sdk;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LealonePlatformTest {

    @Test
    void builderCreatesPlatformInstance() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertNotNull(platform);
        platform.close();
    }

    @Test
    void platformExposesTypedCapabilities() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertNotNull(platform.database());
        assertNotNull(platform.llm());
        assertNotNull(platform.compiler());
        assertNotNull(platform.interactive());
        assertNotNull(platform.llm().providerRegistry());
        assertNotNull(platform.compiler().sourceCompiler());
        assertNotNull(platform.compiler().classCacheManager());
        assertNotNull(platform.compiler().hotLoader());
        assertNotNull(platform.interactive().sessionFactory());
        platform.close();
    }

    @Test
    void databaseCapabilityExposesJdbcHandle() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertTrue(platform.database().jdbcUrl().startsWith("jdbc:lealone:"));
        platform.close();
    }

    @Test
    void runtimeConfigStoreCapabilityIsOptional() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertNotNull(platform.llm().runtimeConfigStore());
        platform.close();
    }

    @Test
    void specDrivenAndPlatformShareProviderRegistry() {
        SpecDriven sdk = SpecDriven.builder().build();

        assertNotNull(sdk.platform());
        assertNotNull(sdk.platform().llm().providerRegistry());
        assertNotNull(sdk.createAgent());
        sdk.close();
    }

    @Test
    void platformUsesExplicitTypedAccessorsRatherThanGenericRegistryMethod() {
        Method[] methods = LealonePlatform.class.getDeclaredMethods();

        assertTrue(Arrays.stream(methods).anyMatch(method -> method.getName().equals("database")));
        assertTrue(Arrays.stream(methods).anyMatch(method -> method.getName().equals("llm")));
        assertTrue(Arrays.stream(methods).anyMatch(method -> method.getName().equals("compiler")));
        assertTrue(Arrays.stream(methods).anyMatch(method -> method.getName().equals("interactive")));
        assertTrue(Arrays.stream(methods).noneMatch(method -> method.getName().equals("capability")));
    }

    @Test
    void platformCloseIsIdempotent() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertDoesNotThrow(() -> {
            platform.close();
            platform.close();
        });
    }
}
