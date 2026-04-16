package org.specdriven.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecDrivenTest {

    @Test
    void builderReturnsNewBuilder() {
        SdkBuilder builder = SpecDriven.builder();
        assertNotNull(builder);
    }

    @Test
    void builderCreatesSdkInstance() {
        SpecDriven sdk = SpecDriven.builder().build();
        assertNotNull(sdk);
        assertNotNull(sdk.platform());
        sdk.close();
    }

    @Test
    void createAgentReturnsNonNull() {
        SpecDriven sdk = SpecDriven.builder().build();
        SdkAgent agent = sdk.createAgent();
        assertNotNull(agent);
        sdk.close();
    }

    @Test
    void platformCoexistsWithSpecDrivenEntryPoint() {
        SpecDriven sdk = SpecDriven.builder().build();

        assertNotNull(sdk.platform().database());
        assertNotNull(sdk.platform().llm());
        assertNotNull(sdk.platform().compiler());
        assertNotNull(sdk.platform().interactive());
        sdk.close();
    }

    @Test
    void closeDoesNotThrowWithNoProviders() {
        SpecDriven sdk = SpecDriven.builder().build();
        assertDoesNotThrow(sdk::close);
    }

    @Test
    void closeIsIdempotent() {
        SpecDriven sdk = SpecDriven.builder().build();
        assertDoesNotThrow(() -> {
            sdk.close();
            sdk.close();
        });
    }

    @Test
    void multipleSdksCanCoexist() {
        SpecDriven sdk1 = SpecDriven.builder().build();
        SpecDriven sdk2 = SpecDriven.builder().systemPrompt("different").build();
        assertNotNull(sdk1);
        assertNotNull(sdk2);
        sdk1.close();
        sdk2.close();
    }

    @Test
    void implementsAutoCloseable() {
        assertTrue(AutoCloseable.class.isAssignableFrom(SpecDriven.class));
    }
}
