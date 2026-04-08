package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SdkBuilderEventTest {

    @Test
    void globalWildcardListenerReceivesAllEvents() {
        List<Event> events = Collections.synchronizedList(new ArrayList<>());

        SpecDriven sdk = SpecDriven.builder()
                .onEvent(events::add)
                .build();

        SdkAgent agent = sdk.createAgent();
        agent.run("hello");

        assertFalse(events.isEmpty(), "Global wildcard listener should receive events");
        assertTrue(events.stream().anyMatch(e -> e.type() == EventType.AGENT_STATE_CHANGED));
        sdk.close();
    }

    @Test
    void globalTypedListenerReceivesOnlyMatchingType() {
        List<Event> errorEvents = Collections.synchronizedList(new ArrayList<>());

        SpecDriven sdk = SpecDriven.builder()
                .onEvent(EventType.ERROR, errorEvents::add)
                .build();

        SdkAgent agent = sdk.createAgent();
        agent.run("hello");

        // No errors in normal run, but listener should only get ERROR events
        assertTrue(errorEvents.stream().allMatch(e -> e.type() == EventType.ERROR));
        sdk.close();
    }

    @Test
    void multipleGlobalListenersWork() {
        List<Event> allEvents = Collections.synchronizedList(new ArrayList<>());
        List<Event> stateEvents = Collections.synchronizedList(new ArrayList<>());

        SpecDriven sdk = SpecDriven.builder()
                .onEvent(allEvents::add)
                .onEvent(EventType.AGENT_STATE_CHANGED, stateEvents::add)
                .build();

        SdkAgent agent = sdk.createAgent();
        agent.run("hello");

        assertFalse(allEvents.isEmpty(), "Wildcard listener should receive all events");
        assertFalse(stateEvents.isEmpty(), "Typed listener should receive state events");
        assertTrue(allEvents.size() >= stateEvents.size(),
                "Wildcard should have at least as many events as typed");
        sdk.close();
    }

    @Test
    void buildWithoutListenersWorksAsBefore() {
        SpecDriven sdk = SpecDriven.builder().build();
        SdkAgent agent = sdk.createAgent();
        String result = agent.run("hello");

        assertEquals("", result);
        sdk.close();
    }
}
