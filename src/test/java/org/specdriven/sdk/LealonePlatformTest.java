package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.agent.DefaultLlmProviderRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LealonePlatformTest {

    @TempDir
    Path tempDir;

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
        assertNotNull(platform.sandlock());
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
        assertTrue(Arrays.stream(methods).anyMatch(method -> method.getName().equals("sandlock")));
        assertTrue(Arrays.stream(methods).noneMatch(method -> method.getName().equals("capability")));
    }

    @Test
    void sandlockUsesSelectedProfileWhenNoExplicitProfileIsRequested() throws Exception {
        Path configPath = writeProfilesConfig();
        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new FakeSandlockRuntime(LealonePlatform.SandlockLaunchCheck.ready(),
                        new LealonePlatform.SandlockProcessOutput(0, "ok", "")))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionResult result = platform.sandlock().execute(List.of("echo", "hello"));

            assertEquals("dev", result.resolvedProfile());
            assertEquals(List.of("echo", "hello"), result.command());
            assertEquals(0, result.exitCode());
            assertEquals("ok", result.stdout());
            assertEquals("", result.stderr());
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockUsesExplicitDeclaredProfileForLaunch() throws Exception {
        Path configPath = writeProfilesConfig();
        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new FakeSandlockRuntime(LealonePlatform.SandlockLaunchCheck.ready(),
                        new LealonePlatform.SandlockProcessOutput(17, "stdout", "stderr")))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionResult result = platform.sandlock()
                    .execute("ci", List.of("python", "-V"));

            assertEquals("ci", result.resolvedProfile());
            assertEquals(List.of("python", "-V"), result.command());
            assertEquals(17, result.exitCode());
            assertEquals("stdout", result.stdout());
            assertEquals("stderr", result.stderr());
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockFailsExplicitlyWhenSandlockIsUnavailable() throws Exception {
        Path configPath = writeProfilesConfig();
        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new FakeSandlockRuntime(
                        LealonePlatform.SandlockLaunchCheck.unavailable("Sandlock is unavailable"), null))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionException error = assertThrows(
                    LealonePlatform.SandlockExecutionException.class,
                    () -> platform.sandlock().execute(List.of("echo", "hello")));

            assertEquals(LealonePlatform.SandlockFailureCode.UNAVAILABLE, error.failureCode());
            assertTrue(error.getMessage().contains("Sandlock is unavailable"));
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockFailsExplicitlyWhenHostIsUnsupported() throws Exception {
        Path configPath = writeProfilesConfig();
        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new FakeSandlockRuntime(
                        LealonePlatform.SandlockLaunchCheck.unsupportedHost("unsupported host"), null))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionException error = assertThrows(
                    LealonePlatform.SandlockExecutionException.class,
                    () -> platform.sandlock().execute(List.of("echo", "hello")));

            assertEquals(LealonePlatform.SandlockFailureCode.UNSUPPORTED_HOST, error.failureCode());
            assertTrue(error.getMessage().contains("unsupported host"));
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockFailsExplicitlyForUnknownRequestedProfile() throws Exception {
        Path configPath = writeProfilesConfig();
        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new FakeSandlockRuntime(LealonePlatform.SandlockLaunchCheck.ready(),
                        new LealonePlatform.SandlockProcessOutput(0, "", "")))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionException error = assertThrows(
                    LealonePlatform.SandlockExecutionException.class,
                    () -> platform.sandlock().execute("prod", List.of("echo", "hello")));

            assertEquals(LealonePlatform.SandlockFailureCode.UNKNOWN_PROFILE, error.failureCode());
            assertTrue(error.getMessage().contains("Unknown requested environment profile 'prod'"));
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockFailsWhenNoEffectiveProfileIsAvailable() {
        LealonePlatform platform = SpecDriven.builder()
                .sandlockRuntime(new FakeSandlockRuntime(LealonePlatform.SandlockLaunchCheck.ready(),
                        new LealonePlatform.SandlockProcessOutput(0, "", "")))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionException error = assertThrows(
                    LealonePlatform.SandlockExecutionException.class,
                    () -> platform.sandlock().execute(List.of("echo", "hello")));

            assertEquals(LealonePlatform.SandlockFailureCode.NO_EFFECTIVE_PROFILE, error.failureCode());
            assertTrue(error.getMessage().contains("No effective environment profile"));
        } finally {
            platform.close();
        }
    }

    @Test
    void platformCloseIsIdempotent() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertDoesNotThrow(() -> {
            platform.close();
            platform.close();
        });
    }

    @Test
    void startCompletesWithoutErrorAndIsIdempotent() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertDoesNotThrow(() -> {
            platform.start();
            platform.start();
        });
        platform.close();
    }

    @Test
    void stopCompletesWithoutErrorAndIsSafeAfterClose() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertDoesNotThrow(() -> {
            platform.close();
            platform.stop();
        });
    }

    @Test
    void closeDelegatesToStopSoSubsequentStopIsIdempotent() {
        LealonePlatform platform = LealonePlatform.builder().buildPlatform();

        assertDoesNotThrow(() -> {
            platform.close();
            platform.stop();
            platform.stop();
        });
    }

    @Test
    void bootstrapServicesAppliesSupportedStatementsIdempotently() throws Exception {
        PlatformConfig config = testPlatformConfig("platform-bootstrap");
        LealonePlatform platform = SpecDriven.builder()
                .platformConfig(config)
                .buildPlatform();
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, """
                -- first supported bootstrap contract
                CREATE TABLE IF NOT EXISTS app_bootstrap_state (
                    id varchar primary key,
                    note varchar
                );

                CREATE SERVICE IF NOT EXISTS `app-bootstrap-svc` (
                    execute(prompt varchar) varchar
                )
                LANGUAGE 'skill'
                PACKAGE 'org.specdriven.skill'
                IMPLEMENT BY 'org.specdriven.skill.executor.SpecDrivenProposeExecutor'
                PARAMETERS (skill_id = 'spec_driven_propose', type = 'agent_skill', version = '1.0.0', author = 'test', skill_dir = '/tmp');
                """);

        try {
            LealonePlatform.ServiceApplicationBootstrapResult first = platform.bootstrapServices(servicesSql);
            LealonePlatform.ServiceApplicationBootstrapResult second = platform.bootstrapServices(servicesSql);

            assertEquals(2, first.appliedStatements());
            assertEquals(first.statements(), second.statements());
            assertTableExists(config.jdbcUrl(), "APP_BOOTSTRAP_STATE");
            assertServiceExists(config.jdbcUrl(), "app-bootstrap-svc");
        } finally {
            platform.close();
        }
    }

    @Test
    void bootstrapServicesRejectsUnsupportedStatementsExplicitly() throws Exception {
        PlatformConfig config = testPlatformConfig("platform-bootstrap-reject");
        LealonePlatform platform = SpecDriven.builder()
                .platformConfig(config)
                .buildPlatform();
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, """
                CREATE TABLE IF NOT EXISTS supported_before_failure (
                    id varchar primary key
                );
                DROP TABLE supported_before_failure;
                """);

        try {
            LealonePlatform.BootstrapValidationException error = assertThrows(
                    LealonePlatform.BootstrapValidationException.class,
                    () -> platform.bootstrapServices(servicesSql));

            assertTrue(error.getMessage().contains("Unsupported services.sql statement"));
            assertFalse(tableExists(config.jdbcUrl(), "SUPPORTED_BEFORE_FAILURE"));
        } finally {
            platform.close();
        }
    }

    @Test
    void bootstrapServicesRejectsNonIdempotentCreateStatementsExplicitly() throws Exception {
        PlatformConfig config = testPlatformConfig("platform-bootstrap-non-idempotent");
        LealonePlatform platform = SpecDriven.builder()
                .platformConfig(config)
                .buildPlatform();
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, """
                CREATE TABLE platform_bootstrap_state (
                    id varchar primary key
                );
                """);

        try {
            LealonePlatform.BootstrapValidationException error = assertThrows(
                    LealonePlatform.BootstrapValidationException.class,
                    () -> platform.bootstrapServices(servicesSql));

            assertTrue(error.getMessage().contains("Unsupported services.sql statement"));
            assertFalse(tableExists(config.jdbcUrl(), "PLATFORM_BOOTSTRAP_STATE"));
        } finally {
            platform.close();
        }
    }

    private PlatformConfig testPlatformConfig(String prefix) {
        String dbName = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return new PlatformConfig(
                "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false",
                tempDir.resolve("cache-" + prefix));
    }

    private void assertTableExists(String jdbcUrl, String tableName) throws Exception {
        assertTrue(tableExists(jdbcUrl, tableName));
    }

    private boolean tableExists(String jdbcUrl, String tableName) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private Path writeProfilesConfig() throws Exception {
        Path configPath = tempDir.resolve("profiles.yaml");
        Files.writeString(configPath, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      jdk:
                        javaHome: /opt/jdk-25
                    ci:
                      python:
                        pythonHome: /opt/python-3.12
                """);
        return configPath;
    }

    private record FakeSandlockRuntime(
            LealonePlatform.SandlockLaunchCheck check,
            LealonePlatform.SandlockProcessOutput output) implements LealonePlatform.SandlockRuntime {

        @Override
        public LealonePlatform.SandlockProcessOutput execute(String resolvedProfile, List<String> command) {
            if (output == null) {
                throw new IllegalStateException("output must not be null when execution is requested");
            }
            return output;
        }
    }

    private void assertServiceExists(String jdbcUrl, String serviceName) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT SERVICE_NAME FROM INFORMATION_SCHEMA.SERVICES WHERE UPPER(SERVICE_NAME) = '"
                             + serviceName.toUpperCase() + "'")) {
            assertTrue(rs.next());
        }
    }
}
