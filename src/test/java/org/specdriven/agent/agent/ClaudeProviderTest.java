package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeProviderTest {

    private static final LlmConfig CONFIG = new LlmConfig(
            "https://api.anthropic.com/v1", "sk-ant-test", "claude-sonnet-4-6", 30, 3);

    @Test
    void configReturnsSuppliedConfig() {
        ClaudeProvider provider = new ClaudeProvider(CONFIG);
        assertSame(CONFIG, provider.config());
    }

    @Test
    void createClientReturnsNonNull() {
        ClaudeProvider provider = new ClaudeProvider(CONFIG);
        LlmClient client = provider.createClient();
        assertNotNull(client);
        assertInstanceOf(ClaudeClient.class, client);
    }

    @Test
    void createClientWithSnapshotUsesSnapshotConfig() {
        ClaudeProvider provider = new ClaudeProvider(CONFIG);
        LlmConfigSnapshot snapshot = new LlmConfigSnapshot(
                "claude",
                "https://claude.example.com/v1",
                "claude-opus",
                45,
                2);

        ClaudeClient client = assertInstanceOf(ClaudeClient.class, provider.createClient(snapshot));

        assertEquals(snapshot.baseUrl(), client.config().baseUrl());
        assertEquals(CONFIG.apiKey(), client.config().apiKey());
        assertEquals(snapshot.model(), client.config().model());
        assertEquals(snapshot.timeout(), client.config().timeout());
        assertEquals(snapshot.maxRetries(), client.config().maxRetries());
    }

    @Test
    void closeCompletesWithoutError() {
        ClaudeProvider provider = new ClaudeProvider(CONFIG);
        assertDoesNotThrow(provider::close);
    }

    @Test
    void nullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ClaudeProvider(null));
    }

    @Test
    void factoryCreatesProvider() {
        ClaudeProviderFactory factory = new ClaudeProviderFactory();
        LlmProvider provider = factory.create(CONFIG);
        assertNotNull(provider);
        assertInstanceOf(ClaudeProvider.class, provider);
        assertSame(CONFIG, provider.config());
    }
}
