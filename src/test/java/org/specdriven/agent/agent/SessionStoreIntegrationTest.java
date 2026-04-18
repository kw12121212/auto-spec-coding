package org.specdriven.agent.agent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.specdriven.agent.testsupport.LealoneTestDb;

/**
 * Verifies that DefaultAgent + LealoneSessionStore persist conversation history
 * so that a reloaded session matches the in-memory state after execution.
 */
@Tag("integration")
class SessionStoreIntegrationTest {

    /** Stub LlmClient that returns a fixed text response immediately. */
    private static final LlmClient STUB_CLIENT =
            messages -> new LlmResponse.TextResponse("task complete");

    /** DefaultAgent subclass that wires in the stub LlmClient. */
    private static class StubAgent extends DefaultAgent {
        @Override
        protected LlmClient createLlmClient(AgentContext context) {
            return STUB_CLIENT;
        }
    }

    @Test
    void executeWithStore_persistsConversationHistory() {
        String sessionId = UUID.randomUUID().toString();
        String jdbcUrl = LealoneTestDb.freshJdbcUrl();

        LealoneSessionStore store = new LealoneSessionStore(jdbcUrl);

        // Build context: one initial user message
        Conversation conv = new Conversation();
        conv.append(new UserMessage("what is 2+2?", System.currentTimeMillis()));

        SimpleAgentContext context = new SimpleAgentContext(
                sessionId,
                Map.of("maxTurns", "10"),
                Collections.emptyMap(),
                conv,
                store);

        StubAgent agent = new StubAgent();
        agent.init(Map.of("maxTurns", "10"));
        agent.start();
        agent.execute(context);

        // In-memory conversation: user message + assistant response from stub
        List<Message> inMemory = conv.history();
        assertTrue(inMemory.size() >= 2, "Conversation should have at least user + assistant messages");

        // Reload from store
        Session loaded = store.load(sessionId).orElseThrow(
                () -> new AssertionError("Session not found in store after execute"));

        List<Message> stored = loaded.conversation().history();
        assertEquals(inMemory.size(), stored.size(),
                "Stored conversation size must match in-memory size");

        for (int i = 0; i < inMemory.size(); i++) {
            assertEquals(inMemory.get(i).role(), stored.get(i).role(),
                    "Message " + i + " role mismatch");
            assertEquals(inMemory.get(i).content(), stored.get(i).content(),
                    "Message " + i + " content mismatch");
        }
    }
}
