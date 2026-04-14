package org.specdriven.agent.interactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Minimal in-memory reference implementation of {@link InteractiveSession}.
 *
 * <p>Carries no dependency on Lealone, {@code DefaultLoopDriver}, or
 * {@code QuestionDeliveryService}. Suitable as a test double and as the stable
 * baseline for later adapter implementations.
 *
 * <p>Output is queued explicitly via {@link #queueOutput(String)}, keeping input
 * and output on separate paths so tests can verify drain semantics independently.
 */
public final class InMemoryInteractiveSession implements InteractiveSession {

    private final String sessionId;
    private InteractiveSessionState state;
    private final List<String> outputBuffer;

    public InMemoryInteractiveSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.state = InteractiveSessionState.NEW;
        this.outputBuffer = new ArrayList<>();
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public synchronized InteractiveSessionState state() {
        return state;
    }

    @Override
    public synchronized void start() {
        if (state != InteractiveSessionState.NEW) {
            throw new IllegalStateException(
                    "start() requires state NEW, current: " + state);
        }
        state = InteractiveSessionState.ACTIVE;
    }

    @Override
    public synchronized void submit(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be null or blank");
        }
        if (state != InteractiveSessionState.ACTIVE) {
            throw new IllegalStateException(
                    "submit() requires state ACTIVE, current: " + state);
        }
    }

    @Override
    public synchronized List<String> drainOutput() {
        if (outputBuffer.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> snapshot = Collections.unmodifiableList(new ArrayList<>(outputBuffer));
        outputBuffer.clear();
        return snapshot;
    }

    @Override
    public synchronized void close() {
        if (state == InteractiveSessionState.CLOSED) {
            return;
        }
        state = InteractiveSessionState.CLOSED;
    }

    /**
     * Queues a message into the pending output buffer.
     * Used by tests and future adapters to inject session output.
     */
    public synchronized void queueOutput(String message) {
        outputBuffer.add(message);
    }

    /**
     * Transitions the session to the {@code ERROR} state.
     * Used by tests to verify close-from-error behavior.
     *
     * @throws IllegalStateException if the session is already {@code CLOSED}
     */
    public synchronized void triggerError() {
        if (state == InteractiveSessionState.CLOSED) {
            throw new IllegalStateException(
                    "Cannot transition to ERROR from CLOSED");
        }
        state = InteractiveSessionState.ERROR;
    }
}
