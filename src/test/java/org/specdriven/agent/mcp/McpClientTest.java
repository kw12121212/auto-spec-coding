package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpClientTest {

    // --- Initialize handshake ---

    @Test
    void initialize_completesAndSetsInitialized(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClient client = new McpClient("python3 " + mockServer, 10)) {
            assertFalse(client.isInitialized());
            client.initialize();
            assertTrue(client.isInitialized());
        }
    }

    @Test
    void initialize_validatesProtocolVersion(@TempDir Path tempDir) throws Exception {
        Path badServer = createBadVersionServer(tempDir);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try (McpClient client = new McpClient("python3 " + badServer, 10)) {
                client.initialize();
            }
        });
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    // --- Tool discovery ---

    @Test
    void toolsList_returnsToolDescriptors(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClient client = new McpClient("python3 " + mockServer, 10)) {
            client.initialize();
            List<Map<String, Object>> tools = client.toolsList();
            assertEquals(2, tools.size());

            Map<String, Object> tool1 = tools.get(0);
            assertEquals("read_file", tool1.get("name"));
            assertEquals("Read a file", tool1.get("description"));
            assertNotNull(tool1.get("inputSchema"));
        }
    }

    // --- Tool invocation ---

    @Test
    void callTool_returnsSuccessResult(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (McpClient client = new McpClient("python3 " + mockServer, 10)) {
            client.initialize();

            McpClient.McpToolResult result = client.callTool("read_file", Map.of("path", "/tmp/test.txt"));
            assertFalse(result.isError());
            assertEquals("file content here", result.extractText());
        }
    }

    @Test
    void callTool_returnsErrorResult(@TempDir Path tempDir) throws Exception {
        Path mockServer = createErrorServer(tempDir);
        try (McpClient client = new McpClient("python3 " + mockServer, 10)) {
            client.initialize();

            McpClient.McpToolResult result = client.callTool("failing_tool", Map.of());
            assertTrue(result.isError());
        }
    }

    // --- Timeout ---

    @Test
    void callTool_timeoutThrowsException(@TempDir Path tempDir) throws Exception {
        Path slowServer = createSlowServer(tempDir);
        try (McpClient client = new McpClient("python3 " + slowServer, 2)) {
            client.initialize();
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> client.callTool("slow_tool", Map.of()));
            assertTrue(ex.getMessage().contains("timed out"));
        }
    }

    // --- Close ---

    @Test
    void close_terminatesProcess(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        McpClient client = new McpClient("python3 " + mockServer, 10);
        client.initialize();
        client.close();
        // No exception = clean shutdown
    }

    @Test
    void invalidCommand_throwsOnInitialize() {
        // "sh -c /nonexistent/command" starts sh successfully, but init will fail
        assertThrows(Exception.class, () -> {
            try (McpClient client = new McpClient("/nonexistent/command", 5)) {
                client.initialize();
            }
        });
    }

    // --- Mock servers ---

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
        pass  # no response for notifications
    elif method == 'tools/list':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'tools':[
            {'name':'read_file','description':'Read a file','inputSchema':{'type':'object','properties':{'path':{'type':'string','description':'File path'}},'required':['path']}},
            {'name':'write_file','description':'Write a file','inputSchema':{'type':'object','properties':{'path':{'type':'string','description':'File path'},'content':{'type':'string','description':'File content'}},'required':['path','content']}}
        ]}})
    elif method == 'tools/call':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'content':[{'type':'text','text':'file content here'}],'isError':False}})
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{}})
        break
""";
        Path scriptFile = tempDir.resolve("mock_mcp_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }

    private static Path createBadVersionServer(Path tempDir) throws IOException {
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
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'protocolVersion':'2099-01-01','capabilities':{}}})
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{}})
        break
""";
        Path scriptFile = tempDir.resolve("bad_version_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }

    private static Path createErrorServer(Path tempDir) throws IOException {
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
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'protocolVersion':'2024-11-05','capabilities':{}}})
    elif method == 'notifications/initialized':
        pass
    elif method == 'tools/call':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'content':[{'type':'text','text':'something went wrong'}],'isError':True}})
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{}})
        break
""";
        Path scriptFile = tempDir.resolve("error_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }

    private static Path createSlowServer(Path tempDir) throws IOException {
        String script = """
import sys, json, time

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
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'protocolVersion':'2024-11-05','capabilities':{}}})
    elif method == 'notifications/initialized':
        pass
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{}})
        break
    else:
        time.sleep(60)  # Never respond to tool calls
""";
        Path scriptFile = tempDir.resolve("slow_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }
}
