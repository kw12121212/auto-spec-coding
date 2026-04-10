package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobileChannelRegistryTest {

    private static MobileChannelHandle stubHandle() {
        return new MobileChannelHandle(
                new LoggingDeliveryChannel(),
                new InMemoryReplyCollector(new QuestionRuntime(new org.specdriven.agent.event.SimpleEventBus()))
        );
    }

    @Test
    void registerAndLookupProvider() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        MobileChannelProvider provider = config -> stubHandle();
        registry.registerProvider("telegram", provider);
        assertSame(provider, registry.provider("telegram"));
    }

    @Test
    void rejectDuplicateProviderName() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        registry.registerProvider("telegram", config -> stubHandle());
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerProvider("telegram", config -> stubHandle()));
    }

    @Test
    void registeredProvidersReturnsAllNames() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        registry.registerProvider("telegram", config -> stubHandle());
        registry.registerProvider("discord", config -> stubHandle());
        assertEquals(Set.of("telegram", "discord"), registry.registeredProviders());
    }

    @Test
    void assembleAllReturnsHandlesInConfigOrder() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        registry.registerProvider("telegram", config -> stubHandle());
        registry.registerProvider("discord", config -> stubHandle());

        List<MobileChannelConfig> configs = List.of(
                new MobileChannelConfig("telegram", "vault:tg"),
                new MobileChannelConfig("discord", "vault:dc")
        );
        List<MobileChannelHandle> handles = registry.assembleAll(configs);
        assertEquals(2, handles.size());
    }

    @Test
    void assembleAllRejectsUnknownType() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        List<MobileChannelConfig> configs = List.of(
                new MobileChannelConfig("slack", "vault:slack")
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.assembleAll(configs));
        assertTrue(ex.getMessage().contains("slack"));
    }

    @Test
    void assembleAllEmptyConfigsReturnsEmptyList() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        registry.registerProvider("telegram", config -> stubHandle());
        assertTrue(registry.assembleAll(List.of()).isEmpty());
    }

    @Test
    void assembleAllNullReturnsEmptyList() {
        MobileChannelRegistry registry = new MobileChannelRegistry();
        assertTrue(registry.assembleAll(null).isEmpty());
    }
}
