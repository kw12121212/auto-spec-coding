package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.agent.*;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.http.RemoteHttpTool;
import org.specdriven.agent.http.RemoteToolRegistrationRequest;
import org.specdriven.agent.permission.LealonePolicyStore;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class SdkAgentTest {

    private final SimpleEventBus eventBus = new SimpleEventBus();

    @Test
    void runWithNoProviderReturnsEmpty() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        String result = agent.run("hello");
        assertEquals("", result);
    }

    @Test
    void getStateBeforeRunIsNull() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        assertNull(agent.getState());
    }

    @Test
    void getStateAfterRunIsStopped() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        agent.run("hello");
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void stopOnFreshAgentDoesNotThrow() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), null, eventBus
        );
        assertDoesNotThrow(agent::stop);
    }

    @Test
    void runWithSystemPrompt() {
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), SdkConfig.defaults(), "You are helpful", eventBus
        );
        String result = agent.run("hello");
        assertEquals("", result);
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void sdkConfigOverridesDefaults() {
        SdkConfig config = new SdkConfig(5, 30, null);
        SdkAgent agent = new SdkAgent(
                null, Collections.emptyMap(), config, null, eventBus
        );
        String result = agent.run("test");
        assertEquals("", result);
    }

    @Test
    void runInvokesRemoteToolCallbackAndContinues() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.start(200, "{\"success\":true,\"output\":\"lookup result\"}", calls)) {
            SpecDriven sdk = SpecDriven.builder()
                    .providerRegistry(new StubRegistry(new StubClient(
                            new LlmResponse.ToolCallResponse(List.of(new ToolCall(
                                    "lookup_success", Map.of("term", "abc"), "call-1"))),
                            new LlmResponse.TextResponse("done"))))
                    .build();
            sdk.registerRemoteTool(new RemoteHttpTool(new RemoteToolRegistrationRequest(
                    "lookup_success",
                    "lookup data",
                    List.of(Map.of("name", "term", "type", "string", "description", "search term", "required", true)),
                    server.url())));
            grantExecute("lookup_success");

            String result = sdk.createAgent().run("use lookup");

            assertEquals("done", result);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void remoteToolCallbackErrorDoesNotTerminateAgentRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.start(200, "{\"success\":false,\"error\":\"not found\"}", calls)) {
            SpecDriven sdk = SpecDriven.builder()
                    .providerRegistry(new StubRegistry(new StubClient(
                            new LlmResponse.ToolCallResponse(List.of(new ToolCall(
                                    "lookup_error", Map.of("term", "missing"), "call-1"))),
                            new LlmResponse.TextResponse("recovered"))))
                    .build();
            sdk.registerRemoteTool(new RemoteHttpTool(new RemoteToolRegistrationRequest(
                    "lookup_error",
                    "lookup data",
                    List.of(),
                    server.url())));
            grantExecute("lookup_error");

            String result = sdk.createAgent().run("use lookup");

            assertEquals("recovered", result);
            assertEquals(1, calls.get());
        }
    }

    private void grantExecute(String toolName) {
        new LealonePolicyStore("jdbc:lealone:embed:agent_db").grant(
                new Permission("execute", toolName, Map.of()),
                new PermissionContext(toolName, "execute", "agent"));
    }

    private record StubClient(LlmResponse... responses) implements LlmClient {
        @Override
        public LlmResponse chat(List<Message> messages) {
            int assistantMessages = 0;
            for (Message message : messages) {
                if (message instanceof AssistantMessage) {
                    assistantMessages++;
                }
            }
            return responses[Math.min(assistantMessages, responses.length - 1)];
        }
    }

    private record StubRegistry(LlmClient client) implements LlmProviderRegistry {
        @Override public void register(String name, LlmProvider provider) {}
        @Override public LlmProvider provider(String name) { throw new IllegalArgumentException(name); }
        @Override public LlmProvider defaultProvider() { throw new IllegalStateException("no default provider"); }
        @Override public Set<String> providerNames() { return Set.of(); }
        @Override public void remove(String name) {}
        @Override public void setDefault(String name) {}
        @Override public SkillRoute route(String skillName) { return null; }
        @Override public void addSkillRoute(String skillName, SkillRoute route) {}
        @Override public LlmClient createClientForSession(String sessionId) { return client; }
        @Override public void close() {}
    }

    private static class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        static TestHttpServer start(int status, String body, AtomicInteger calls) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/tool", exchange -> {
                calls.incrementAndGet();
                byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, response.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            server.start();
            return new TestHttpServer(server);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/tool";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
