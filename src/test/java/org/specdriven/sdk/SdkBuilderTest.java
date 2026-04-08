package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.agent.LlmProvider;
import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.agent.DefaultLlmProviderRegistry;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolParameter;
import org.specdriven.agent.tool.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SdkBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildWithDefaultsReturnsNonNull() {
        SpecDriven sdk = SpecDriven.builder().build();
        assertNotNull(sdk);
        sdk.close();
    }

    @Test
    void buildWithManualProviderRegistry() {
        LlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        SpecDriven sdk = SpecDriven.builder()
                .providerRegistry(registry)
                .build();
        assertNotNull(sdk);
        sdk.close();
    }

    @Test
    void manualRegistryOverridesConfig() throws Exception {
        // Create a minimal config file
        Path configPath = tempDir.resolve("agent.yaml");
        Files.writeString(configPath, "agent:\n  name: test\n");

        LlmProviderRegistry manualRegistry = new DefaultLlmProviderRegistry();
        SpecDriven sdk = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(manualRegistry)
                .build();
        assertNotNull(sdk);
        sdk.close();
    }

    @Test
    void registerToolsMakesThemAvailable() {
        Tool dummyTool = new DummyTool("test-tool", "A test tool");
        SpecDriven sdk = SpecDriven.builder()
                .registerTool(dummyTool)
                .build();

        SdkAgent agent = sdk.createAgent();
        assertNotNull(agent);
        sdk.close();
    }

    @Test
    void registerMultipleTools() {
        SpecDriven sdk = SpecDriven.builder()
                .registerTool(new DummyTool("tool-a", "Tool A"))
                .registerTool(new DummyTool("tool-b", "Tool B"))
                .build();
        assertNotNull(sdk);
        sdk.close();
    }

    @Test
    void systemPromptIsSetOnBuilder() {
        SpecDriven sdk = SpecDriven.builder()
                .systemPrompt("You are a code reviewer")
                .build();
        assertNotNull(sdk);
        sdk.close();
    }

    @Test
    void sdkConfigOverridesDefaults() {
        SdkConfig custom = new SdkConfig(10, 30, "custom prompt");
        SpecDriven sdk = SpecDriven.builder()
                .sdkConfig(custom)
                .build();
        assertNotNull(sdk);
        sdk.close();
    }

    @Test
    void invalidConfigPathThrowsSdkException() {
        SdkBuilder builder = SpecDriven.builder()
                .config(tempDir.resolve("nonexistent.yaml"));
        assertThrows(SdkException.class, builder::build);
    }

    @Test
    void configWithProvidersAutoAssembles() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        Files.writeString(configPath, """
            llm:
              providers:
                test-provider:
                  baseUrl: "https://api.example.com/v1"
                  apiKey: "test-key"
                  model: "test-model"
              default: "test-provider"
            """);

        SpecDriven sdk = SpecDriven.builder()
                .config(configPath)
                .build();
        assertNotNull(sdk);
        sdk.close();
    }

    /** Minimal Tool implementation for testing. */
    static class DummyTool implements Tool {
        private final String name;
        private final String description;

        DummyTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public List<ToolParameter> getParameters() { return List.of(); }
        @Override public ToolResult execute(ToolInput input, ToolContext context) {
            return new ToolResult.Success("dummy result");
        }
    }
}
