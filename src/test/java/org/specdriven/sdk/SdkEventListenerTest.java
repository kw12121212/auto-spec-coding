package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SdkEventListenerTest {

    @Test
    void lambdaIsAcceptedAsListener() {
        SdkEventListener listener = e -> {};
        assertNotNull(listener);
    }

    @Test
    void methodReferenceWorks() {
        SdkEventListener listener = this::handleEvent;
        assertNotNull(listener);
    }

    @Test
    void listenerReceivesEvent() {
        Event[] captured = {null};
        SdkEventListener listener = e -> captured[0] = e;

        Event event = new Event(EventType.ERROR, System.currentTimeMillis(), "test", Map.of());
        listener.accept(event);

        assertNotNull(captured[0]);
        assertEquals(EventType.ERROR, captured[0].type());
        assertEquals("test", captured[0].source());
    }

    private void handleEvent(Event e) {
        // no-op
    }
}
