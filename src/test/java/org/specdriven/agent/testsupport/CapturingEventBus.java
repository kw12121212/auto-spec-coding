package org.specdriven.agent.testsupport;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class CapturingEventBus implements EventBus {

    private final CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(Event event) {
        events.add(event);
    }

    @Override
    public void subscribe(EventType type, Consumer<Event> listener) {
    }

    @Override
    public void unsubscribe(EventType type, Consumer<Event> listener) {
    }

    public List<Event> getEvents() {
        return List.copyOf(events);
    }

    public List<Event> eventsOfType(EventType type) {
        return events.stream().filter(e -> e.type() == type).toList();
    }

    public void clear() {
        events.clear();
    }
}
