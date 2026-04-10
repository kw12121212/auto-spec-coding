package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobileChannelConfigTest {

    @Test
    void constructionWithAllFields() {
        MobileChannelConfig config = new MobileChannelConfig("telegram", "vault:tg-bot", Map.of("chatId", "123"));
        assertEquals("telegram", config.channelType());
        assertEquals("vault:tg-bot", config.vaultKey());
        assertEquals(Map.of("chatId", "123"), config.overrides());
    }

    @Test
    void constructionWithDefaults() {
        MobileChannelConfig config = new MobileChannelConfig("discord", "vault:discord");
        assertEquals("discord", config.channelType());
        assertEquals("vault:discord", config.vaultKey());
        assertTrue(config.overrides().isEmpty());
    }

    @Test
    void rejectsNullChannelType() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileChannelConfig(null, "vault:key"));
    }

    @Test
    void rejectsBlankChannelType() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileChannelConfig("  ", "vault:key"));
    }

    @Test
    void rejectsNullVaultKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileChannelConfig("telegram", null));
    }

    @Test
    void rejectsBlankVaultKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobileChannelConfig("telegram", "  "));
    }

    @Test
    void nullOverridesDefaultsToEmpty() {
        MobileChannelConfig config = new MobileChannelConfig("telegram", "vault:key", null);
        assertTrue(config.overrides().isEmpty());
    }

    @Test
    void overridesAreDefensiveCopy() {
        Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("k", "v");
        MobileChannelConfig config = new MobileChannelConfig("telegram", "vault:key", mutable);
        mutable.put("k2", "v2");
        assertEquals(1, config.overrides().size());
    }
}
