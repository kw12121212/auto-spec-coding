package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LspClientTest {

    // --- JSON-RPC message framing ---

    @Test
    void sendAndReceive_correlatesRequestResponse(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (LspClient client = new LspClient(
                List.of("python3", mockServer.toString()), 10)) {

            client.initialize(Path.of("/tmp").toUri().toString());

            Map<String, Object> response = client.hover("file:///Test.java", 0, 0);
            assertNotNull(response.get("id"));
            assertNotNull(response.get("result"));
        }
    }

    @Test
    void requestIds_incrementMonotonically(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (LspClient client = new LspClient(
                List.of("python3", mockServer.toString()), 10)) {

            client.initialize(Path.of("/tmp").toUri().toString());

            Map<String, Object> r1 = client.hover("file:///Test.java", 1, 0);
            Map<String, Object> r2 = client.hover("file:///Test.java", 2, 0);

            int id1 = ((Number) r1.get("id")).intValue();
            int id2 = ((Number) r2.get("id")).intValue();
            // initialize is id=1, first hover is id=2, second hover is id=3
            assertEquals(2, id1);
            assertEquals(3, id2);
        }
    }

    // --- Lifecycle ---

    @Test
    void initialize_completesWithoutError(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (LspClient client = new LspClient(
                List.of("python3", mockServer.toString()), 10)) {

            assertFalse(client.isInitialized());
            client.initialize(Path.of("/tmp").toUri().toString());
            assertTrue(client.isInitialized());
        }
    }

    @Test
    void close_terminatesServerProcess(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        LspClient client = new LspClient(
                List.of("python3", mockServer.toString()), 10);
        client.initialize(Path.of("/tmp").toUri().toString());

        client.close();
        // Process should be dead after close
        // No exception means shutdown/exit completed cleanly
    }

    // --- Error handling ---

    @Test
    void invalidServerCommand_throwsIOException() {
        assertThrows(IOException.class, () -> {
            try (LspClient ignored = new LspClient(
                    List.of("/nonexistent/command"), 5)) {
            }
        });
    }

    @Test
    void requestTimeout_returnsError(@TempDir Path tempDir) throws Exception {
        // Create a server that never responds to hover requests
        Path slowServer = createSlowServer(tempDir);
        try (LspClient client = new LspClient(
                List.of("python3", slowServer.toString()), 10)) {

            client.initialize(Path.of("/tmp").toUri().toString());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> client.hover("file:///Test.java", 0, 0));
            assertTrue(ex.getMessage().contains("timed out"));
        }
    }

    // --- Diagnostics ---

    @Test
    void diagnostics_collectedFromNotification(@TempDir Path tempDir) throws Exception {
        Path mockServer = createMockServer(tempDir);
        try (LspClient client = new LspClient(
                List.of("python3", mockServer.toString()), 10)) {

            client.initialize(Path.of("/tmp").toUri().toString());

            String uri = Path.of(tempDir.toString()).resolve("Test.java").toUri().toString();
            client.textDocumentDidOpen(uri, "java", "class Test {");

            List<Map<String, Object>> diags = client.waitForDiagnostics(uri, 5000);
            assertFalse(diags.isEmpty());
            assertEquals("test error", diags.get(0).get("message"));
        }
    }

    // --- Helpers ---

    private static Path createMockServer(Path tempDir) throws IOException {
        String script = """
import sys, json

def read_msg():
    length = 0
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            return None
        line = line.decode('utf-8').strip()
        if line == '':
            break
        if line.startswith('Content-Length:'):
            length = int(line.split(':')[1].strip())
    if length == 0:
        return None
    body = sys.stdin.buffer.read(length).decode('utf-8')
    return json.loads(body)

def send_msg(msg):
    body = json.dumps(msg).encode('utf-8')
    sys.stdout.buffer.write(f'Content-Length: {len(body)}\\r\\n\\r\\n'.encode('utf-8'))
    sys.stdout.buffer.write(body)
    sys.stdout.buffer.flush()

while True:
    msg = read_msg()
    if msg is None:
        break
    method = msg.get('method', '')
    mid = msg.get('id')
    if method == 'initialize':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'capabilities':{}}})
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':None})
        break
    elif method == 'textDocument/hover':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'contents':'hover result'}})
    elif method == 'textDocument/definition':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'uri':'file:///test.java','range':{'start':{'line':0,'character':0},'end':{'line':0,'character':5}}}})
    elif method == 'textDocument/references':
        send_msg({'jsonrpc':'2.0','id':mid,'result':[{'uri':'file:///test.java','range':{'start':{'line':0,'character':0},'end':{'line':0,'character':5}}}]})
    elif method == 'textDocument/documentSymbol':
        send_msg({'jsonrpc':'2.0','id':mid,'result':[{'name':'TestClass','kind':5,'range':{'start':{'line':0,'character':0},'end':{'line':10,'character':1}}}]})
    elif method == 'textDocument/didOpen':
        uri = msg.get('params',{}).get('textDocument',{}).get('uri','')
        send_msg({'jsonrpc':'2.0','method':'textDocument/publishDiagnostics','params':{'uri':uri,'diagnostics':[{'range':{'start':{'line':0,'character':0},'end':{'line':0,'character':5}},'severity':1,'message':'test error'}]}})
""";
        Path scriptFile = tempDir.resolve("mock_lsp_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }

    private static Path createSlowServer(Path tempDir) throws IOException {
        // Server that handles initialize but ignores other requests
        String script = """
import sys, json, time

def read_msg():
    length = 0
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            return None
        line = line.decode('utf-8').strip()
        if line == '':
            break
        if line.startswith('Content-Length:'):
            length = int(line.split(':')[1].strip())
    if length == 0:
        return None
    body = sys.stdin.buffer.read(length).decode('utf-8')
    return json.loads(body)

def send_msg(msg):
    body = json.dumps(msg).encode('utf-8')
    sys.stdout.buffer.write(f'Content-Length: {len(body)}\\r\\n\\r\\n'.encode('utf-8'))
    sys.stdout.buffer.write(body)
    sys.stdout.buffer.flush()

while True:
    msg = read_msg()
    if msg is None:
        break
    method = msg.get('method', '')
    mid = msg.get('id')
    if method == 'initialize':
        send_msg({'jsonrpc':'2.0','id':mid,'result':{'capabilities':{}}})
    elif method == 'shutdown':
        send_msg({'jsonrpc':'2.0','id':mid,'result':None})
        break
    else:
        # Never respond to other requests — causes timeout
        pass
""";
        Path scriptFile = tempDir.resolve("slow_lsp_server.py");
        Files.writeString(scriptFile, script);
        return scriptFile;
    }
}
