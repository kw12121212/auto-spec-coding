package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.vault.SecretVault;
import org.specdriven.agent.vault.VaultException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TelegramChannelProviderTest {

    private QuestionRuntime runtime;
    private InMemoryTestVault vault;
    private TelegramChannelProvider provider;

    @BeforeEach
    void setUp() {
        runtime = new QuestionRuntime(new SimpleEventBus());
        vault = new InMemoryTestVault();
        vault.put("tg.token", "123456:ABC-DEF");
        provider = new TelegramChannelProvider(runtime, vault);
    }

    @Test
    void createReturnsHandleWithCorrectTypes() {
        MobileChannelConfig config = new MobileChannelConfig("telegram", "tg",
                Map.of("chatId", "-1001234567890"));

        MobileChannelHandle handle = provider.create(config);

        assertInstanceOf(TelegramDeliveryChannel.class, handle.channel());
        assertInstanceOf(TelegramReplyCollector.class, handle.collector());
    }

    @Test
    void createThrowsOnMissingChatId() {
        MobileChannelConfig config = new MobileChannelConfig("telegram", "tg");

        MobileAdapterException ex = assertThrows(MobileAdapterException.class,
                () -> provider.create(config));
        assertEquals("telegram", ex.channelType());
        assertTrue(ex.getMessage().contains("chatId"));
    }

    @Test
    void createThrowsOnMissingVaultEntry() {
        MobileChannelConfig config = new MobileChannelConfig("telegram", "missing_key",
                Map.of("chatId", "-1001234567890"));

        assertThrows(VaultException.class, () -> provider.create(config));
    }

    /** Simple in-memory vault for testing. */
    static class InMemoryTestVault implements SecretVault {
        private final Map<String, String> store = new HashMap<>();

        void put(String key, String value) {
            store.put(key, value);
        }

        @Override
        public String get(String key) {
            String v = store.get(key);
            if (v == null) throw new VaultException("Key not found: " + key);
            return v;
        }

        @Override
        public void set(String key, String plaintext, String description) {
            store.put(key, plaintext);
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public List<org.specdriven.agent.vault.VaultEntry> list() {
            return List.of();
        }

        @Override
        public boolean exists(String key) {
            return store.containsKey(key);
        }
    }
}
