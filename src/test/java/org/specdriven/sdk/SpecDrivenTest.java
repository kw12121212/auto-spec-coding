package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.LealoneQuestionStore;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionDeliveryService;
import org.specdriven.agent.question.QuestionStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpecDrivenTest {

    @TempDir
    Path tempDir;

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

    @Test
    void deliveryServiceUsesPlatformJdbcUrl() {
        PlatformConfig config = new PlatformConfig(
                "jdbc:lealone:embed:delivery_service_platform_db",
                Path.of(System.getProperty("java.io.tmpdir"), "delivery-service-platform-cache"));

        SpecDriven sdk = SpecDriven.builder()
                .platformConfig(config)
                .build();
        try {
            QuestionDeliveryService service = sdk.deliveryService();
            assertNotNull(service);

            Question question = new Question(
                    "q-delivery-platform",
                    "session-platform",
                    "Need approval?",
                    "Platform migration verification",
                    "Approve if platform-backed JDBC is used",
                    QuestionStatus.WAITING_FOR_ANSWER,
                    QuestionCategory.PERMISSION_CONFIRMATION,
                    DeliveryMode.PAUSE_WAIT_HUMAN);

            service.deliver(question);

            LealoneQuestionStore customStore = new LealoneQuestionStore(
                    sdk.eventBus(),
                    sdk.platform().database().jdbcUrl());
            try {
                assertTrue(customStore.findPending("session-platform").isPresent());
            } finally {
                customStore.delete("q-delivery-platform");
            }
        } finally {
            sdk.close();
        }
    }

    @Test
    void sdkStillExposesDefaultPlatformJdbcUrlWithoutExplicitPlatformConfig() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            assertEquals(PlatformConfig.defaults().jdbcUrl(), sdk.platform().database().jdbcUrl());

            QuestionDeliveryService service = sdk.deliveryService();
            if (service == null) {
                return;
            }

            Question question = new Question(
                    "q-delivery-default",
                    "session-default",
                    "Use default DB?",
                    "Default compatibility verification",
                    "Keep default behavior unchanged",
                    QuestionStatus.WAITING_FOR_ANSWER,
                    QuestionCategory.PERMISSION_CONFIRMATION,
                    DeliveryMode.PAUSE_WAIT_HUMAN);

            service.deliver(question);

            LealoneQuestionStore defaultStore = new LealoneQuestionStore(
                    sdk.eventBus(),
                    PlatformConfig.defaults().jdbcUrl());
            try {
                assertTrue(defaultStore.findPending("session-default").isPresent());
            } finally {
                defaultStore.delete("q-delivery-default");
            }
        } finally {
            sdk.close();
        }
    }

    @Test
    void bootstrapServicesUsesAssembledPlatformJdbcRuntime() throws Exception {
        Path servicesSql = tempDir.resolve("services.sql");
        Files.writeString(servicesSql, """
                CREATE TABLE IF NOT EXISTS sdk_bootstrap_state (
                    id varchar primary key
                );
                """);

        SpecDriven sdk = SpecDriven.builder().build();
        try {
            LealonePlatform.ServiceApplicationBootstrapResult result = sdk.bootstrapServices(servicesSql);
            assertEquals(1, result.appliedStatements());
            assertEquals(servicesSql.toAbsolutePath().normalize(), result.sourcePath());
            assertTrue(tableExists(sdk.platform().database().jdbcUrl(), "SDK_BOOTSTRAP_STATE"));

            QuestionDeliveryService service = sdk.deliveryService();
            assertNotNull(service);
        } finally {
            sdk.close();
        }
    }

    @Test
    void workflowDeclarationAndRuntimeSurfaceAreAvailable() {
        SpecDriven sdk = SpecDriven.builder().build();
        try {
            sdk.declareWorkflow("invoice-approval");

            WorkflowInstanceView started = sdk.startWorkflow("invoice-approval", Map.of("invoiceId", "inv-1"));

            assertNotNull(started.workflowId());
            assertEquals("invoice-approval", started.workflowName());
            assertEquals(WorkflowStatus.ACCEPTED, started.status());
            assertEquals(started.workflowId(), sdk.workflowState(started.workflowId()).workflowId());
        } finally {
            sdk.close();
        }
    }

    private boolean tableExists(String jdbcUrl, String tableName) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }
}
