package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinMobileAdaptersTest {

    private MobileChannelRegistry registry;
    private QuestionRuntime runtime;
    private TelegramChannelProviderTest.InMemoryTestVault vault;

    @BeforeEach
    void setUp() {
        registry = new MobileChannelRegistry();
        runtime = new QuestionRuntime(new SimpleEventBus());
        vault = new TelegramChannelProviderTest.InMemoryTestVault();
    }

    @Test
    void registerAllPopulatesBothProviders() {
        BuiltinMobileAdapters.registerAll(registry, runtime, vault);
        assertEquals(Set.of("telegram", "discord"), registry.registeredProviders());
    }

    @Test
    void constantValues() {
        assertEquals("telegram", BuiltinMobileAdapters.TELEGRAM);
        assertEquals("discord", BuiltinMobileAdapters.DISCORD);
    }

    @Test
    void registryWasEmptyBeforeRegisterAll() {
        assertTrue(registry.registeredProviders().isEmpty());
    }
}
