package org.specdriven.agent.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

class EventSystemTest {

    @Test
    void eventConstruction() {
        Event event = new Event(EventType.TOOL_EXECUTED, 12345L, "bash", Map.of("exitCode", 0));
        assertEquals(EventType.TOOL_EXECUTED, event.type());
        assertEquals(12345L, event.timestamp());
        assertEquals("bash", event.source());
        assertEquals(0, event.metadata().get("exitCode"));
    }

    @Test
    void eventTypeCoversAllValues() {
        EventType[] types = EventType.values();
        assertEquals(38, types.length);
        assertNotNull(EventType.valueOf("TOOL_EXECUTED"));
        assertNotNull(EventType.valueOf("AGENT_STATE_CHANGED"));
        assertNotNull(EventType.valueOf("TASK_CREATED"));
        assertNotNull(EventType.valueOf("TASK_COMPLETED"));
        assertNotNull(EventType.valueOf("TEAM_CREATED"));
        assertNotNull(EventType.valueOf("TEAM_DISSOLVED"));
        assertNotNull(EventType.valueOf("CRON_TRIGGERED"));
        assertNotNull(EventType.valueOf("BACKGROUND_TOOL_STARTED"));
        assertNotNull(EventType.valueOf("BACKGROUND_TOOL_STOPPED"));
        assertNotNull(EventType.valueOf("SERVER_TOOL_READY"));
        assertNotNull(EventType.valueOf("SERVER_TOOL_FAILED"));
        assertNotNull(EventType.valueOf("VAULT_SECRET_CREATED"));
        assertNotNull(EventType.valueOf("VAULT_SECRET_DELETED"));
        assertNotNull(EventType.valueOf("LLM_CONFIG_CHANGED"));
        assertNotNull(EventType.valueOf("ERROR"));
        assertNotNull(EventType.valueOf("LLM_CACHE_HIT"));
        assertNotNull(EventType.valueOf("LLM_CACHE_MISS"));
        assertNotNull(EventType.valueOf("TOOL_CACHE_HIT"));
        assertNotNull(EventType.valueOf("TOOL_CACHE_MISS"));
        assertNotNull(EventType.valueOf("QUESTION_CREATED"));
        assertNotNull(EventType.valueOf("QUESTION_ANSWERED"));
        assertNotNull(EventType.valueOf("QUESTION_ESCALATED"));
        assertNotNull(EventType.valueOf("QUESTION_EXPIRED"));
        assertNotNull(EventType.valueOf("DELIVERY_ATTEMPTED"));
        assertNotNull(EventType.valueOf("DELIVERY_SUCCEEDED"));
        assertNotNull(EventType.valueOf("DELIVERY_FAILED"));
        assertNotNull(EventType.valueOf("LOOP_STARTED"));
        assertNotNull(EventType.valueOf("LOOP_PAUSED"));
        assertNotNull(EventType.valueOf("LOOP_RESUMED"));
        assertNotNull(EventType.valueOf("LOOP_STOPPED"));
        assertNotNull(EventType.valueOf("LOOP_ITERATION_COMPLETED"));
        assertNotNull(EventType.valueOf("LOOP_PROGRESS_SAVED"));
        assertNotNull(EventType.valueOf("LOOP_CONTEXT_EXHAUSTED"));
        assertNotNull(EventType.valueOf("LOOP_QUESTION_ROUTED"));
        assertNotNull(EventType.valueOf("LOOP_QUESTION_ANSWERED"));
        assertNotNull(EventType.valueOf("LOOP_QUESTION_ESCALATED"));
        assertNotNull(EventType.valueOf("SKILL_HOT_LOAD_OPERATION"));
        assertNotNull(EventType.valueOf("LOOP_ERROR"));
    }

    @Test
    void eventBusPublishSubscribe() {
        List<Event> received = new ArrayList<>();
        EventBus bus = new SimpleEventBus();

        Consumer<Event> listener = received::add;
        bus.subscribe(EventType.TOOL_EXECUTED, listener);

        Event event = new Event(EventType.TOOL_EXECUTED, 1L, "test", Map.of());
        bus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void eventBusUnsubscribe() {
        List<Event> received = new ArrayList<>();
        EventBus bus = new SimpleEventBus();

        Consumer<Event> listener = received::add;
        bus.subscribe(EventType.TOOL_EXECUTED, listener);
        bus.unsubscribe(EventType.TOOL_EXECUTED, listener);

        bus.publish(new Event(EventType.TOOL_EXECUTED, 1L, "test", Map.of()));
        assertTrue(received.isEmpty());
    }

    @Test
    void eventBusOnlyReceivesSubscribedType() {
        List<Event> received = new ArrayList<>();
        EventBus bus = new SimpleEventBus();

        bus.subscribe(EventType.ERROR, received::add);

        bus.publish(new Event(EventType.TOOL_EXECUTED, 1L, "test", Map.of()));
        bus.publish(new Event(EventType.ERROR, 2L, "test", Map.of()));

        assertEquals(1, received.size());
        assertEquals(EventType.ERROR, received.get(0).type());
    }
}
