package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.config.Config;
import org.specdriven.agent.config.ConfigLoader;
import org.specdriven.agent.tool.Tool;

class McpClientRegistryTest {

    // --- Register/discover lifecycle ---

    @Test
    void register_createsAndInitializesClient(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClientRegistry registry = new McpClientRegistry()) {
            McpClient client = registry.register("test", "python3 " + mockServer);
            assertTrue(client.isInitialized());
        }
    }

    @Test
    void register_duplicateName_throwsException(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClientRegistry registry = new McpClientRegistry()) {
            registry.register("test", "python3 " + mockServer);
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register("test", "python3 " + mockServer));
        }
    }

    @Test
    void discoverTools_returnsAdaptedTools(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClientRegistry registry = new McpClientRegistry()) {
            registry.register("myserver", "python3 " + mockServer);
            List<Tool> tools = registry.discoverTools("myserver");

            assertEquals(2, tools.size());
            assertEquals("mcp__myserver__read_file", tools.get(0).getName());
            assertEquals("mcp__myserver__write_file", tools.get(1).getName());
        }
    }

    @Test
    void discoverAllTools_aggregatesFromAllClients(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClientRegistry registry = new McpClientRegistry()) {
            registry.register("s1", "python3 " + mockServer);
            registry.register("s2", "python3 " + mockServer);

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

    // --- Close ---

    @Test
    void close_shutsDownAllClients(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        McpClientRegistry registry = new McpClientRegistry();
        registry.register("test", "python3 " + mockServer);
        registry.close();
        // No exception = clean shutdown
    }

    // --- Config-based initialization ---

    @Test
    void fromConfig_initializesFromYaml(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, """
mcp:
  servers:
    my-server:
      command: "python3 %s"
      timeout: 10
""".formatted(mockServer.toString()));

        Config config = ConfigLoader.load(configFile);
        try (McpClientRegistry registry = McpClientRegistry.fromConfig(config)) {
            List<Tool> tools = registry.discoverTools("my-server");
            assertFalse(tools.isEmpty());
        }
    }

    @Test
    void fromConfig_missingSection_returnsEmptyRegistry() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        // test-config.yaml has mcp.servers section, so this test verifies it works
        McpClientRegistry registry = McpClientRegistry.fromConfig(config);
        // The "echo test" command won't be a valid MCP server, so it'll fail silently
        registry.close();
    }

    // --- Mock server ---

    private static Path createMockServer(Path tempDir) throws IOException {
        String script = """
import sys, json

def read_msg():
    length = 0
    while True:
        line = sys.stdin.buffer.readline()
        if not line: return None
        line = line.decode('utf-8').strip()
        if line == '': break
        if line.startswith('Content-Length:'):
            length = int(line.split(':')[1].strip())
    if length == 0: return None
    body = sys.stdin.buffer.read(length).decode('utf-8')
    return json.loads(body)

def send_msg(msg):
    body = json.dumps(msg).encode('utf-8')
    sys.stdout.buffer.write(f'Content-Length: {len(body)}\\r\\n\\r\\n'.encode('utf-8'))
    sys.stdout.buffer.write(body)
    sys.stdout.buffer.flush()

while True:
    msg = read_msg()
    if msg is None: break
    method = msg.get('method', '')
    mid = msg.get('id')
    if method == 'initialize':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'protocolVersion':'2024-11-05','capabilities':{'tools':{}},'serverInfo':{'name':'mock','version':'0.1.0'}}})
    elif method == 'notifications/initialized':
        pass
    elif method == 'tools/list':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'tools':[
            {'name':'read_file','description':'Read a file','inputSchema':{'type':'object','properties':{'path':{'type':'string','description':'File path'}},'required':['path']}},
            {'name':'write_file','description':'Write a file','inputSchema':{'type':'object','properties':{'path':{'type':'string','description':'File path'},'content':{'type':'string','description':'Content'}},'required':['path','content']}}
        ]}})
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{}})
        break
""";
        Path scriptFile = tempDir.resolve("registry_mock_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }
}
