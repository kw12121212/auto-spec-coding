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
        LealonePlatform platform = SpecDriven.builder().buildPlatform();
        assertNotNull(sdk);
        assertNotNull(platform);
        platform.close();
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
    void buildPlatformWithManualProviderRegistry() {
        LlmProviderRegistry registry = new DefaultLlmProviderRegistry();
        LealonePlatform platform = SpecDriven.builder()
                .providerRegistry(registry)
                .buildPlatform();

        assertNotNull(platform);
        assertSame(registry, platform.llm().providerRegistry());
        platform.close();
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
        SdkException ex = assertThrows(SdkException.class, builder::build);
        assertInstanceOf(SdkConfigException.class, ex);
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

    @Test
    void platformConfigCustomJdbcUrlIsReflectedInDatabaseCapability() {
        PlatformConfig config = new PlatformConfig("jdbc:lealone:embed:custom_db",
                PlatformConfig.defaults().compileCachePath());

        LealonePlatform platform = SpecDriven.builder()
                .platformConfig(config)
                .buildPlatform();

        assertEquals("jdbc:lealone:embed:custom_db", platform.database().jdbcUrl());
        platform.close();
    }

    @Test
    void platformWithoutExplicitConfigUsesDefaultJdbcUrl() {
        LealonePlatform platform = SpecDriven.builder().buildPlatform();

        assertEquals(PlatformConfig.defaults().jdbcUrl(), platform.database().jdbcUrl());
        platform.close();
    }

    @Test
    void platformConfigCustomCompileCachePathIsUsedByClassCacheManager() throws Exception {
        Path customCacheDir = tempDir.resolve("custom-cache");
        PlatformConfig config = new PlatformConfig(PlatformConfig.defaults().jdbcUrl(), customCacheDir);

        LealonePlatform platform = SpecDriven.builder()
                .platformConfig(config)
                .buildPlatform();

        platform.compiler().classCacheManager().resolveClassDir("test-skill", "abc123");
        assertTrue(Files.exists(customCacheDir.resolve("test-skill").resolve("abc123")));
        platform.close();
    }

    @Test
    void buildUsesDefaultEnvironmentProfileWhenNoneRequested() throws Exception {
        Path configPath = tempDir.resolve("profiles.yaml");
        Files.writeString(configPath, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      runtime:
                        home: /work/dev-home
                        path:
                          - /opt/jdk-25/bin
                        cache:
                          maven: /work/dev-cache/maven
                          npm: /work/dev-cache/npm
                          go: /work/dev-cache/go
                          pip: /work/dev-cache/pip
                      jdk:
                        javaHome: /opt/jdk-25
                    ci:
                      runtime:
                        home: /work/ci-home
                        path:
                          - /opt/python-3.12/bin
                        cache:
                          maven: /work/ci-cache/maven
                          npm: /work/ci-cache/npm
                          go: /work/ci-cache/go
                          pip: /work/ci-cache/pip
                      python:
                        pythonHome: /opt/python-3.12
                """);

        try (SpecDriven sdk = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .build()) {
            assertEquals("dev", sdk.configMap().get("environmentProfiles.selected"));
            assertEquals("/work/dev-home", sdk.configMap().get("environmentProfiles.selected.runtime.home"));
            assertEquals("/opt/jdk-25", sdk.configMap().get("environmentProfiles.selected.jdk.javaHome"));
            assertNull(sdk.configMap().get("environmentProfiles.selected.python.pythonHome"));
        }
    }

    @Test
    void explicitEnvironmentProfileOverridesDefault() throws Exception {
        Path configPath = tempDir.resolve("profiles.yaml");
        Files.writeString(configPath, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      runtime:
                        home: /work/dev-home
                        path:
                          - /opt/jdk-25/bin
                        cache:
                          maven: /work/dev-cache/maven
                          npm: /work/dev-cache/npm
                          go: /work/dev-cache/go
                          pip: /work/dev-cache/pip
                      jdk:
                        javaHome: /opt/jdk-25
                    ci:
                      runtime:
                        home: /work/ci-home
                        path:
                          - /opt/python-3.12/bin
                        cache:
                          maven: /work/ci-cache/maven
                          npm: /work/ci-cache/npm
                          go: /work/ci-cache/go
                          pip: /work/ci-cache/pip
                      python:
                        pythonHome: /opt/python-3.12
                """);

        try (SpecDriven sdk = SpecDriven.builder()
                .config(configPath)
                .environmentProfile("ci")
                .providerRegistry(new DefaultLlmProviderRegistry())
                .build()) {
            assertEquals("ci", sdk.configMap().get("environmentProfiles.selected"));
            assertEquals("/work/ci-cache/pip", sdk.configMap().get("environmentProfiles.selected.runtime.cache.pip"));
            assertEquals("/opt/python-3.12", sdk.configMap().get("environmentProfiles.selected.python.pythonHome"));
            assertNull(sdk.configMap().get("environmentProfiles.selected.jdk.javaHome"));
        }
    }

    @Test
    void sdkPlatformSandlockUsesExistingAssemblyProfileSelection() throws Exception {
        Path configPath = tempDir.resolve("profiles.yaml");
        Files.writeString(configPath, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      runtime:
                        home: /work/dev-home
                        path:
                          - /opt/jdk-25/bin
                        cache:
                          maven: /work/dev-cache/maven
                          npm: /work/dev-cache/npm
                          go: /work/dev-cache/go
                          pip: /work/dev-cache/pip
                      jdk:
                        javaHome: /opt/jdk-25
                    ci:
                      runtime:
                        home: /work/ci-home
                        path:
                          - /opt/python-3.12/bin
                        cache:
                          maven: /work/ci-cache/maven
                          npm: /work/ci-cache/npm
                          go: /work/ci-cache/go
                          pip: /work/ci-cache/pip
                      python:
                        pythonHome: /opt/python-3.12
                """);

        try (SpecDriven sdk = SpecDriven.builder()
                .config(configPath)
                .environmentProfile("ci")
                .sandlockRuntime(new BuilderSandlockRuntime())
                .providerRegistry(new DefaultLlmProviderRegistry())
                .build()) {
            LealonePlatform.SandlockExecutionResult result = sdk.platform().sandlock().execute(List.of("python", "-V"));

            assertEquals("ci", result.resolvedProfile());
            assertEquals(List.of("python", "-V"), result.command());
            assertEquals(3, result.exitCode());
            assertEquals("python 3.12", result.stdout());
            assertEquals("", result.stderr());
        }
    }

    @Test
    void unknownExplicitEnvironmentProfileFailsBuild() throws Exception {
        Path configPath = tempDir.resolve("profiles.yaml");
        Files.writeString(configPath, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      runtime:
                        home: /work/dev-home
                        path:
                          - /opt/jdk-25/bin
                        cache:
                          maven: /work/dev-cache/maven
                          npm: /work/dev-cache/npm
                          go: /work/dev-cache/go
                          pip: /work/dev-cache/pip
                      jdk:
                        javaHome: /opt/jdk-25
                """);

        SdkException ex = assertThrows(SdkException.class, () -> SpecDriven.builder()
                .config(configPath)
                .environmentProfile("ci")
                .providerRegistry(new DefaultLlmProviderRegistry())
                .build());

        assertInstanceOf(SdkConfigException.class, ex);
        assertTrue(ex.getMessage().contains("Failed to load config"));
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

    private static final class BuilderSandlockRuntime implements LealonePlatform.SandlockRuntime {

        @Override
        public LealonePlatform.SandlockLaunchCheck check() {
            return LealonePlatform.SandlockLaunchCheck.ready();
        }

        @Override
        public LealonePlatform.SandlockProcessOutput execute(LealonePlatform.SandlockProfile resolvedProfile,
                                                             List<String> command) {
            return new LealonePlatform.SandlockProcessOutput(3, "python 3.12", "");
        }
    }
}
