package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.vault.VaultException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiscordChannelProviderTest {

    private QuestionRuntime runtime;
    private TelegramChannelProviderTest.InMemoryTestVault vault;
    private DiscordChannelProvider provider;

    @BeforeEach
    void setUp() {
        runtime = new QuestionRuntime(new SimpleEventBus());
        vault = new TelegramChannelProviderTest.InMemoryTestVault();
        vault.put("dc.webhookUrl", "https://discord.com/api/webhooks/123/abc");
        vault.put("dc.secret", "my-webhook-secret");
        provider = new DiscordChannelProvider(runtime, vault);
    }

    @Test
    void createReturnsHandleWithCorrectTypes() {
        MobileChannelConfig config = new MobileChannelConfig("discord", "dc",
                Map.of("callbackBaseUrl", "https://example.com/callback"));

        MobileChannelHandle handle = provider.create(config);

        assertInstanceOf(DiscordDeliveryChannel.class, handle.channel());
        assertInstanceOf(DiscordReplyCollector.class, handle.collector());
    }

    @Test
    void createThrowsOnMissingCallbackBaseUrl() {
        MobileChannelConfig config = new MobileChannelConfig("discord", "dc");

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> provider.create(config));
        assertEquals("discord", ex.channelType());
        assertTrue(ex.getMessage().contains("callbackBaseUrl"));
    }

    @Test
    void createThrowsOnMissingVaultEntry() {
        MobileChannelConfig config = new MobileChannelConfig("discord", "missing_key",
                Map.of("callbackBaseUrl", "https://example.com/callback"));

        assertThrows(VaultException.class, () -> provider.create(config));
    }
}
