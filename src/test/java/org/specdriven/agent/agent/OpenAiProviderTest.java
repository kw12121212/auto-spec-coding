package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiProviderTest {

    private static final LlmConfig CONFIG = new LlmConfig(
            "https://api.openai.com/v1", "sk-test", "gpt-4", 30, 3);

    @Test
    void configReturnsSuppliedConfig() {
        OpenAiProvider provider = new OpenAiProvider(CONFIG);
        assertSame(CONFIG, provider.config());
    }

    @Test
    void createClientReturnsNonNull() {
        OpenAiProvider provider = new OpenAiProvider(CONFIG);
        LlmClient client = provider.createClient();
        assertNotNull(client);
        assertInstanceOf(OpenAiClient.class, client);
    }

    @Test
    void closeCompletesWithoutError() {
        OpenAiProvider provider = new OpenAiProvider(CONFIG);
        assertDoesNotThrow(provider::close);
    }

    @Test
    void nullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new OpenAiProvider(null));
    }

    @Test
    void factoryCreatesProvider() {
        OpenAiProviderFactory factory = new OpenAiProviderFactory();
        LlmProvider provider = factory.create(CONFIG);
        assertNotNull(provider);
        assertInstanceOf(OpenAiProvider.class, provider);
        assertSame(CONFIG, provider.config());
    }
}
