package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.testsupport.MockLspServerMain;
import org.specdriven.agent.testsupport.SubprocessTestCommand;

class LspClientTest {

    @Test
    void sendAndReceive_correlatesRequestResponse() throws Exception {
        try (LspClient client = new LspClient(commandList("standard"), 10)) {
            client.initialize(Path.of("/tmp").toUri().toString());

            Map<String, Object> response = client.hover("file:///Test.java", 0, 0);
            assertNotNull(response.get("id"));
            assertNotNull(response.get("result"));
        }
    }

    @Test
    void requestIds_incrementMonotonically() throws Exception {
        try (LspClient client = new LspClient(commandList("standard"), 10)) {
            client.initialize(Path.of("/tmp").toUri().toString());

            Map<String, Object> r1 = client.hover("file:///Test.java", 1, 0);
            Map<String, Object> r2 = client.hover("file:///Test.java", 2, 0);

            int id1 = ((Number) r1.get("id")).intValue();
            int id2 = ((Number) r2.get("id")).intValue();
            assertEquals(2, id1);
            assertEquals(3, id2);
        }
    }

    @Test
    void initialize_completesWithoutError() throws Exception {
        try (LspClient client = new LspClient(commandList("standard"), 10)) {
            assertFalse(client.isInitialized());
            client.initialize(Path.of("/tmp").toUri().toString());
            assertTrue(client.isInitialized());
        }
    }

    @Test
    void close_terminatesServerProcess() throws Exception {
        LspClient client = new LspClient(commandList("standard"), 10);
        client.initialize(Path.of("/tmp").toUri().toString());
        client.close();
    }

    @Test
    void invalidServerCommand_throwsIOException() {
        assertThrows(IOException.class, () -> {
            try (LspClient ignored = new LspClient(List.of("/nonexistent/command"), 5)) {
            }
        });
    }

    @Test
    void requestTimeout_returnsError() throws Exception {
        try (LspClient client = new LspClient(commandList("slow"), 10)) {
            client.initialize(Path.of("/tmp").toUri().toString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> client.hover("file:///Test.java", 0, 0));
            assertTrue(ex.getMessage().contains("timed out"));
        }
    }

    @Test
    void diagnostics_collectedFromNotification(@TempDir Path tempDir) throws Exception {
        try (LspClient client = new LspClient(commandList("standard"), 10)) {
            client.initialize(Path.of("/tmp").toUri().toString());

            String uri = tempDir.resolve("Test.java").toUri().toString();
            client.textDocumentDidOpen(uri, "java", "class Test {");

            List<Map<String, Object>> diags = client.waitForDiagnostics(uri, 5000);
            assertFalse(diags.isEmpty());
            assertEquals("test error", diags.get(0).get("message"));
        }
    }

    private static List<String> commandList(String mode) {
        return SubprocessTestCommand.javaCommandList(MockLspServerMain.class, mode);
    }
}
