package org.specdriven.agent.event;

import java.util.function.Consumer;

/**
 * Pub/sub contract for dispatching events to listeners.
 */
public interface EventBus {

    /**
     * Publishes an event to all subscribers of its type.
     */
    void publish(Event event);

    /**
     * Subscribes a listener to events of the given type.
     */
    void subscribe(EventType type, Consumer<Event> listener);

    /**
     * Unsubscribes a previously registered listener.
     */
    void unsubscribe(EventType type, Consumer<Event> listener);
}
