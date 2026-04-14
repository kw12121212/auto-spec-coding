package org.specdriven.agent.interactive;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InteractiveSessionTest {

    private InMemoryInteractiveSession session;

    @BeforeEach
    void setUp() {
        session = new InMemoryInteractiveSession();
    }

    // --- Lifecycle: initial state ---

    @Test
    void newSessionStateIsNew() {
        assertEquals(InteractiveSessionState.NEW, session.state());
    }

    @Test
    void sessionIdIsNonBlankAndStable() {
        String id = session.sessionId();
        assertNotNull(id);
        assertFalse(id.isBlank());
        assertEquals(id, session.sessionId());
    }

    // --- Lifecycle: start ---

    @Test
    void startTransitionsToActive() {
        session.start();
        assertEquals(InteractiveSessionState.ACTIVE, session.state());
    }

    @Test
    void startFromActiveThrows() {
        session.start();
        IllegalStateException ex = assertThrows(IllegalStateException.class, session::start);
        assertTrue(ex.getMessage().contains("ACTIVE"));
        assertEquals(InteractiveSessionState.ACTIVE, session.state());
    }

    @Test
    void startFromClosedThrows() {
        session.close();
        assertThrows(IllegalStateException.class, session::start);
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void startFromErrorThrows() {
        session.triggerError();
        assertThrows(IllegalStateException.class, session::start);
        assertEquals(InteractiveSessionState.ERROR, session.state());
    }

    // --- Lifecycle: submit ---

    @Test
    void submitBeforeStartThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> session.submit("SHOW STATUS"));
        assertTrue(ex.getMessage().contains("NEW"));
        assertEquals(InteractiveSessionState.NEW, session.state());
    }

    @Test
    void submitNullInputThrows() {
        session.start();
        assertThrows(IllegalArgumentException.class, () -> session.submit(null));
    }

    @Test
    void submitEmptyInputThrows() {
        session.start();
        assertThrows(IllegalArgumentException.class, () -> session.submit(""));
    }

    @Test
    void submitBlankInputThrows() {
        session.start();
        assertThrows(IllegalArgumentException.class, () -> session.submit("   "));
    }

    @Test
    void submitToClosedSessionThrows() {
        session.start();
        session.close();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> session.submit("SHOW STATUS"));
        assertTrue(ex.getMessage().contains("CLOSED"));
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void submitToErrorSessionThrows() {
        session.start();
        session.triggerError();
        assertThrows(IllegalStateException.class, () -> session.submit("SHOW STATUS"));
        assertEquals(InteractiveSessionState.ERROR, session.state());
    }

    @Test
    void submitValidInputDoesNotThrow() {
        session.start();
        assertDoesNotThrow(() -> session.submit("SHOW STATUS"));
    }

    // --- Drain output ---

    @Test
    void drainOutputWhenEmptyReturnsEmptyList() {
        session.start();
        List<String> output = session.drainOutput();
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    void drainOutputReturnsMessagesInEmissionOrder() {
        session.start();
        session.queueOutput("first");
        session.queueOutput("second");
        session.queueOutput("third");

        List<String> output = session.drainOutput();
        assertEquals(List.of("first", "second", "third"), output);
    }

    @Test
    void drainOutputClearsPendingBuffer() {
        session.start();
        session.queueOutput("msg");

        session.drainOutput();
        List<String> second = session.drainOutput();
        assertTrue(second.isEmpty());
    }

    // --- Lifecycle: close ---

    @Test
    void closeFromNewTransitionsToClosed() {
        session.close();
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void closeFromActiveTransitionsToClosed() {
        session.start();
        session.close();
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void closeFromErrorTransitionsToClosed() {
        session.triggerError();
        session.close();
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void closeIsIdempotent() {
        session.start();
        session.close();
        assertDoesNotThrow(session::close);
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }
}
