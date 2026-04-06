package org.specdriven.agent.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe EventBus implementation using CopyOnWriteArrayList per event type.
 */
public class SimpleEventBus implements EventBus {

    private final Map<EventType, CopyOnWriteArrayList<Consumer<Event>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(Event event) {
        List<Consumer<Event>> list = listeners.get(event.type());
        if (list != null) {
            for (Consumer<Event> listener : list) {
                listener.accept(event);
            }
        }
    }

    @Override
    public void subscribe(EventType type, Consumer<Event> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribe(EventType type, Consumer<Event> listener) {
        CopyOnWriteArrayList<Consumer<Event>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }
}
