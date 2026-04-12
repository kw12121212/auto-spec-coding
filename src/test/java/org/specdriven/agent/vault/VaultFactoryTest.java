package org.specdriven.agent.vault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("VaultMasterKey")
class VaultFactoryTest {

    @BeforeEach
    void setUp() {
        VaultMasterKey.setEnvSource(() -> "test-master-key");
    }

    @AfterEach
    void tearDown() {
        VaultMasterKey.reset();
    }

    @Test
    void createWithEventBus_returnsUsableVault() {
        String dbName = "test_factory_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        CapturingEventBus eventBus = new CapturingEventBus();

        LealoneVault vault = VaultFactory.create(eventBus, jdbcUrl);
        vault.set("test_key", "test_value", "test");
        assertEquals("test_value", vault.get("test_key"));
    }

    @Test
    void createWithEventBusAndCustomJdbcUrl_returnsUsableVault() {
        String dbName = "test_factory_custom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String customJdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        CapturingEventBus eventBus = new CapturingEventBus();

        LealoneVault vault = VaultFactory.create(eventBus, customJdbcUrl);
        vault.set("custom_key", "custom_value", "custom test");
        assertEquals("custom_value", vault.get("custom_key"));
    }

    @Test
    void createWithNullEventBus_returnsUsableVault() {
        String dbName = "test_factory_null_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";

        LealoneVault vault = VaultFactory.create(null, jdbcUrl);
        vault.set("null_bus_key", "value", "null bus test");
        assertEquals("value", vault.get("null_bus_key"));
    }

    @Test
    void createdVaultPublishesEvents() {
        String dbName = "test_factory_events_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        CapturingEventBus eventBus = new CapturingEventBus();

        LealoneVault vault = VaultFactory.create(eventBus, jdbcUrl);
        vault.set("event_key", "val", "desc");

        assertFalse(eventBus.captured.isEmpty());
        assertEquals(EventType.VAULT_SECRET_CREATED, eventBus.captured.get(0).type());
    }

    private static class CapturingEventBus implements EventBus {
        final List<Event> captured = new ArrayList<>();

        @Override
        public void publish(Event event) {
            captured.add(event);
        }

        @Override
        public void subscribe(EventType type, Consumer<Event> listener) {
        }

        @Override
        public void unsubscribe(EventType type, Consumer<Event> listener) {
        }
    }
}
