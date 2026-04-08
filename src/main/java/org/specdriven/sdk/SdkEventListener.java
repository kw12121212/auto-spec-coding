package org.specdriven.sdk;

import org.specdriven.agent.event.Event;

import java.util.function.Consumer;

/**
 * Functional interface for receiving SDK agent events.
 * Extends {@link Consumer}&lt;{@link Event}&gt; for compatibility with the internal EventBus.
 */
@FunctionalInterface
public interface SdkEventListener extends Consumer<Event> {
}
