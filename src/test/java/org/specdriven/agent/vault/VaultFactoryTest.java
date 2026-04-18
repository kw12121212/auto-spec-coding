package org.specdriven.agent.vault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

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
        CapturingEventBus eventBus = new CapturingEventBus();

        LealoneVault vault = VaultFactory.create(eventBus, LealoneTestDb.freshJdbcUrl());
        vault.set("test_key", "test_value", "test");
        assertEquals("test_value", vault.get("test_key"));
    }

    @Test
    void createWithEventBusAndCustomJdbcUrl_returnsUsableVault() {
        CapturingEventBus eventBus = new CapturingEventBus();

        LealoneVault vault = VaultFactory.create(eventBus, LealoneTestDb.freshJdbcUrl());
        vault.set("custom_key", "custom_value", "custom test");
        assertEquals("custom_value", vault.get("custom_key"));
    }

    @Test
    void createWithNullEventBus_returnsUsableVault() {
        LealoneVault vault = VaultFactory.create(null, LealoneTestDb.freshJdbcUrl());
        vault.set("null_bus_key", "value", "null bus test");
        assertEquals("value", vault.get("null_bus_key"));
    }

    @Test
    void createdVaultPublishesEvents() {
        CapturingEventBus eventBus = new CapturingEventBus();

        LealoneVault vault = VaultFactory.create(eventBus, LealoneTestDb.freshJdbcUrl());
        vault.set("event_key", "val", "desc");

        assertFalse(eventBus.getEvents().isEmpty());
        assertEquals(EventType.VAULT_SECRET_CREATED, eventBus.getEvents().get(0).type());
    }
}
