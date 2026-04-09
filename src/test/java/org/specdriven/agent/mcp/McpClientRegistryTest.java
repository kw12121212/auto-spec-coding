package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.config.Config;
import org.specdriven.agent.config.ConfigLoader;
import org.specdriven.agent.testsupport.MockMcpServerMain;
import org.specdriven.agent.testsupport.SubprocessTestCommand;
import org.specdriven.agent.tool.Tool;

class McpClientRegistryTest {

    @Test
    void register_createsAndInitializesClient() throws Exception {
        try (McpClientRegistry registry = new McpClientRegistry()) {
            McpClient client = registry.register("test", command("standard"));
            assertTrue(client.isInitialized());
        }
    }

    @Test
    void register_duplicateName_throwsException() throws Exception {
        try (McpClientRegistry registry = new McpClientRegistry()) {
            registry.register("test", command("standard"));
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register("test", command("standard")));
        }
    }

    @Test
    void discoverTools_returnsAdaptedTools() throws Exception {
        try (McpClientRegistry registry = new McpClientRegistry()) {
            registry.register("myserver", command("standard"));
            List<Tool> tools = registry.discoverTools("myserver");

            assertEquals(2, tools.size());
            assertEquals("mcp__myserver__read_file", tools.get(0).getName());
            assertEquals("mcp__myserver__write_file", tools.get(1).getName());
        }
    }

    @Test
    void discoverAllTools_aggregatesFromAllClients() throws Exception {
        try (McpClientRegistry registry = new McpClientRegistry()) {
            registry.register("s1", command("standard"));
            registry.register("s2", command("standard"));

            List<Tool> allTools = registry.discoverAllTools();
            assertEquals(4, allTools.size());
        }
    }

    @Test
    void discoverTools_unknownName_throwsException() {
        try (McpClientRegistry registry = new McpClientRegistry()) {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.discoverTools("nonexistent"));
        }
    }

    @Test
    void close_shutsDownAllClients() throws Exception {
        McpClientRegistry registry = new McpClientRegistry();
        registry.register("test", command("standard"));
        registry.close();
    }

    @Test
    void fromConfig_initializesFromYaml(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, """
mcp:
  servers:
    my-server:
      command: "%s"
      timeout: 10
""".formatted(command("standard")));

        Config config = ConfigLoader.load(configFile);
        try (McpClientRegistry registry = McpClientRegistry.fromConfig(config)) {
            List<Tool> tools = registry.discoverTools("my-server");
            assertFalse(tools.isEmpty());
        }
    }

    @Test
    void fromConfig_missingSection_returnsEmptyRegistry() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        McpClientRegistry registry = McpClientRegistry.fromConfig(config);
        registry.close();
    }

    private static String command(String mode) {
        return SubprocessTestCommand.shellSafeJavaCommand(MockMcpServerMain.class, mode);
    }
}
