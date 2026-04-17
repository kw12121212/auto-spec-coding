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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    void sandlockAppliesSelectedProfileIsolationEnvironment() throws Exception {
        Path configPath = writeProfilesConfig();
        AtomicReference<LealonePlatform.SandlockProfile> capturedProfile = new AtomicReference<>();
        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new CapturingSandlockRuntime(capturedProfile))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionResult result = platform.sandlock().execute(List.of("echo", "hello"));

            assertEquals("dev", result.resolvedProfile());
            assertEquals("/work/dev-home", capturedProfile.get().isolatedHome());
            assertEquals(List.of("/opt/jdk-25/bin", "/opt/node-22/bin"), capturedProfile.get().executableSearchPaths());
            assertEquals("/work/dev-cache/maven", capturedProfile.get().cacheRoots().get("maven"));
            assertEquals("/work/dev-cache/pip", capturedProfile.get().cacheRoots().get("pip"));
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
    void sandlockUsesBundledEntryByDefaultWhenPresent() throws Exception {
        Path configPath = writeProfilesConfig();
        Path bundledEntry = bundledEntryPath();
        Files.createDirectories(bundledEntry.getParent());
        Files.writeString(bundledEntry, "#!/bin/sh\nprintf 'bundled:%s\\n' \"$0\"\n");
        bundledEntry.toFile().setExecutable(true);

        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new LealonePlatform.SystemSandlockRuntime(Map.of(), tempDir))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionResult result = platform.sandlock().execute(List.of("echo", "hello"));

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("bundled:" + bundledEntry.toAbsolutePath().normalize()));
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockUsesExplicitOverrideInsteadOfBundledEntry() throws Exception {
        Path configPath = writeProfilesConfig();
        Path bundledEntry = bundledEntryPath();
        Path overrideEntry = tempDir.resolve("custom").resolve("sandlock");
        Files.createDirectories(bundledEntry.getParent());
        Files.createDirectories(overrideEntry.getParent());
        Files.writeString(bundledEntry, "#!/bin/sh\nprintf 'bundled:%s\\n' \"$0\"\n");
        Files.writeString(overrideEntry, "#!/bin/sh\nprintf 'override:%s\\n' \"$0\"\n");
        bundledEntry.toFile().setExecutable(true);
        overrideEntry.toFile().setExecutable(true);

        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new LealonePlatform.SystemSandlockRuntime(
                        Map.of("SPEC_DRIVEN_SANDLOCK_ENTRY", overrideEntry.toString()), tempDir))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionResult result = platform.sandlock().execute(List.of("echo", "hello"));

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("override:" + overrideEntry.toAbsolutePath().normalize()));
            assertFalse(result.stdout().contains("bundled:"));
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockFailsExplicitlyWhenOverrideIsInvalid() throws Exception {
        Path configPath = writeProfilesConfig();
        Path bundledEntry = bundledEntryPath();
        Files.createDirectories(bundledEntry.getParent());
        Files.writeString(bundledEntry, "#!/bin/sh\nprintf 'bundled:%s\\n' \"$0\"\n");
        bundledEntry.toFile().setExecutable(true);

        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new LealonePlatform.SystemSandlockRuntime(
                        Map.of("SPEC_DRIVEN_SANDLOCK_ENTRY", tempDir.resolve("missing-sandlock").toString()), tempDir))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionException error = assertThrows(
                    LealonePlatform.SandlockExecutionException.class,
                    () -> platform.sandlock().execute(List.of("echo", "hello")));

            assertEquals(LealonePlatform.SandlockFailureCode.UNAVAILABLE, error.failureCode());
            assertTrue(error.getMessage().contains("SPEC_DRIVEN_SANDLOCK_ENTRY"));
            assertFalse(error.getMessage().contains("repository-bundled entry was not found"));
        } finally {
            platform.close();
        }
    }

    @Test
    void sandlockFailsExplicitlyWhenBundledEntryMissing() throws Exception {
        Path configPath = writeProfilesConfig();
        Path bundledEntry = bundledEntryPath();
        Files.deleteIfExists(bundledEntry);
        Files.createDirectories(bundledEntry.getParent());

        LealonePlatform platform = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .sandlockRuntime(new LealonePlatform.SystemSandlockRuntime(Map.of(), tempDir))
                .buildPlatform();

        try {
            LealonePlatform.SandlockExecutionException error = assertThrows(
                    LealonePlatform.SandlockExecutionException.class,
                    () -> platform.sandlock().execute(List.of("echo", "hello")));

            assertEquals(LealonePlatform.SandlockFailureCode.UNAVAILABLE, error.failureCode());
            assertTrue(error.getMessage().contains("repository-bundled entry"));
            assertTrue(error.getMessage().contains(bundledEntry.toAbsolutePath().normalize().toString()));
        } finally {
            platform.close();
        }
    }

    @Test
    void systemSandlockRuntimeDestroysProcessWhenInterrupted() throws Exception {
        Path entry = bundledEntryPath();
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, "#!/bin/sh\nshift 4\nexec \"$@\"\n");
        entry.toFile().setExecutable(true);

        LealonePlatform.SystemSandlockRuntime runtime = new LealonePlatform.SystemSandlockRuntime(Map.of(), tempDir);
        LealonePlatform.SandlockProfile profile = new LealonePlatform.SandlockProfile(
                "dev",
                tempDir.resolve("home").toString(),
                List.of("/usr/bin", "/bin"),
                Map.of(),
                Map.of(
                        "maven", tempDir.resolve("cache/maven").toString(),
                        "npm", tempDir.resolve("cache/npm").toString(),
                        "go", tempDir.resolve("cache/go").toString(),
                        "pip", tempDir.resolve("cache/pip").toString()),
                Map.of());
        Path pidFile = tempDir.resolve("child.pid").toAbsolutePath().normalize();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Long> childPid = new AtomicReference<>();

        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                runtime.execute(profile, List.of(
                        "/bin/sh",
                        "-c",
                        "echo $$ > '" + pidFile + "'; while :; do sleep 0.1; done"));
                failure.set(new AssertionError("expected execution to be interrupted"));
            } catch (InterruptedException expected) {
                // expected
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        for (int i = 0; i < 50; i++) {
            if (Files.isRegularFile(pidFile)) {
                childPid.set(Long.parseLong(Files.readString(pidFile).trim()));
                break;
            }
            Thread.sleep(100);
        }

        assertNotNull(childPid.get(), "expected child process to start");
        worker.interrupt();
        worker.join(5000);

        assertFalse(worker.isAlive(), "interrupted execution should finish promptly");
        assertNotNull(childPid.get(), "expected child process pid to be captured");
        assertTrue(failure.get() == null, failure.get() == null ? "" : failure.get().toString());

        boolean exited = false;
        for (int i = 0; i < 20; i++) {
            if (ProcessHandle.of(childPid.get()).isEmpty() || !ProcessHandle.of(childPid.get()).orElseThrow().isAlive()) {
                exited = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(exited, "interrupted Sandlock execution should destroy the launched process");
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
                      runtime:
                        home: /work/dev-home
                        path:
                          - /opt/jdk-25/bin
                          - /opt/node-22/bin
                        cache:
                          maven: /work/dev-cache/maven
                          npm: /work/dev-cache/npm
                          go: /work/dev-cache/go
                          pip: /work/dev-cache/pip
                      jdk:
                        javaHome: /opt/jdk-25
                      node:
                        nodeHome: /opt/node-22
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
        return configPath;
    }

    private Path bundledEntryPath() {
        return tempDir.resolve("depends").resolve("sandlock").resolve("v0.6.0")
                .resolve("linux-x86_64").resolve("sandlock");
    }

    private record FakeSandlockRuntime(
            LealonePlatform.SandlockLaunchCheck check,
            LealonePlatform.SandlockProcessOutput output) implements LealonePlatform.SandlockRuntime {

        @Override
        public LealonePlatform.SandlockProcessOutput execute(LealonePlatform.SandlockProfile resolvedProfile,
                                                             List<String> command) {
            if (output == null) {
                throw new IllegalStateException("output must not be null when execution is requested");
            }
            return output;
        }
    }

    private static final class CapturingSandlockRuntime implements LealonePlatform.SandlockRuntime {

        private final AtomicReference<LealonePlatform.SandlockProfile> capturedProfile;

        private CapturingSandlockRuntime(AtomicReference<LealonePlatform.SandlockProfile> capturedProfile) {
            this.capturedProfile = capturedProfile;
        }

        @Override
        public LealonePlatform.SandlockLaunchCheck check() {
            return LealonePlatform.SandlockLaunchCheck.ready();
        }

        @Override
        public LealonePlatform.SandlockProcessOutput execute(LealonePlatform.SandlockProfile resolvedProfile,
                                                             List<String> command) {
            capturedProfile.set(resolvedProfile);
            return new LealonePlatform.SandlockProcessOutput(0, "ok", "");
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
