package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.lang.reflect.Method;
import java.util.Arrays;
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
