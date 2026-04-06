package org.specdriven.agent.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

class SimpleEventBusTest {

    @Test
    void subscribeAndPublish() {
        List<Event> received = new ArrayList<>();
        SimpleEventBus bus = new SimpleEventBus();
        bus.subscribe(EventType.TOOL_EXECUTED, received::add);

        Event event = new Event(EventType.TOOL_EXECUTED, 1L, "bash", Map.of("exitCode", 0));
        bus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void unsubscribeRemovesListener() {
        List<Event> received = new ArrayList<>();
        SimpleEventBus bus = new SimpleEventBus();

        Consumer<Event> listener = received::add;
        bus.subscribe(EventType.ERROR, listener);
        bus.unsubscribe(EventType.ERROR, listener);

        bus.publish(new Event(EventType.ERROR, 1L, "test", Map.of()));
        assertTrue(received.isEmpty());
    }

    @Test
    void typeIsolation() {
        List<Event> received = new ArrayList<>();
        SimpleEventBus bus = new SimpleEventBus();

        bus.subscribe(EventType.TASK_CREATED, received::add);
        bus.publish(new Event(EventType.TOOL_EXECUTED, 1L, "test", Map.of()));
        bus.publish(new Event(EventType.TASK_CREATED, 2L, "test", Map.of()));

        assertEquals(1, received.size());
        assertEquals(EventType.TASK_CREATED, received.get(0).type());
    }

    @Test
    void multipleListenersOnSameType() {
        List<Event> r1 = new ArrayList<>();
        List<Event> r2 = new ArrayList<>();
        SimpleEventBus bus = new SimpleEventBus();

        bus.subscribe(EventType.CRON_TRIGGERED, r1::add);
        bus.subscribe(EventType.CRON_TRIGGERED, r2::add);

        Event event = new Event(EventType.CRON_TRIGGERED, 1L, "cron", Map.of());
        bus.publish(event);

        assertEquals(1, r1.size());
        assertEquals(1, r2.size());
    }

    @Test
    void concurrentPublishSafety() throws Exception {
        SimpleEventBus bus = new SimpleEventBus();
        List<Event> received = Collections.synchronizedList(new ArrayList<>());
        bus.subscribe(EventType.TOOL_EXECUTED, received::add);

        int count = 100;
        CountDownLatch latch = new CountDownLatch(count);
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                final int idx = i;
                exec.submit(() -> {
                    bus.publish(new Event(EventType.TOOL_EXECUTED, idx, "test", Map.of()));
                    latch.countDown();
                });
            }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(count, received.size());
    }

    @Test
    void publishWithNoSubscribersDoesNotThrow() {
        SimpleEventBus bus = new SimpleEventBus();
        assertDoesNotThrow(() -> bus.publish(new Event(EventType.ERROR, 1L, "test", Map.of())));
    }
}
