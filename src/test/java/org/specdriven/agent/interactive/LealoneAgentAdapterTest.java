package org.specdriven.agent.interactive;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;

class LealoneAgentAdapterTest {

    @Test
    void startActivatesSessionAndOpensExecutionSession() {
        RecordingFactory factory = new RecordingFactory();
        LealoneAgentAdapter session = new LealoneAgentAdapter(factory);

        String sessionId = session.sessionId();
        session.start();

        assertEquals(InteractiveSessionState.ACTIVE, session.state());
        assertNotNull(factory.openedSession);
        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
        assertEquals(sessionId, session.sessionId());
    }

    @Test
    void closeReleasesExecutionSessionAndIsIdempotent() {
        RecordingFactory factory = new RecordingFactory();
        LealoneAgentAdapter session = new LealoneAgentAdapter(factory);
        session.start();

        session.close();
        session.close();

        assertEquals(InteractiveSessionState.CLOSED, session.state());
        assertTrue(factory.openedSession.closed);
        assertEquals(1, factory.openedSession.closeCount);
    }

    @Test
    void closedSessionRejectsLaterInput() {
        LealoneAgentAdapter session = new LealoneAgentAdapter(new RecordingFactory());
        session.start();
        session.close();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> session.submit("SHOW STATUS"));

        assertTrue(exception.getMessage().contains("CLOSED"));
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void submittedInputReachesExecutionSessionAndOutputDrainsInEmissionOrder() {
        RecordingFactory factory = new RecordingFactory();
        factory.nextOutputs.add(List.of("service_a", "service_b"));
        LealoneAgentAdapter session = new LealoneAgentAdapter(factory);
        session.start();

        session.submit("SHOW SERVICES");

        assertEquals(List.of("SHOW SERVICES"), factory.openedSession.inputs);
        assertEquals(List.of("service_a", "service_b"), session.drainOutput());
        assertTrue(session.drainOutput().isEmpty());
    }

    @Test
    void executionFailureTransitionsToErrorAndPreservesPendingOutput() {
        RecordingFactory factory = new RecordingFactory();
        factory.nextOutputs.add(List.of("before failure"));
        LealoneAgentAdapter session = new LealoneAgentAdapter(factory);
        session.start();
        session.submit("SHOW STATUS");
        factory.openedSession.failure = new RuntimeException("boom");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> session.submit("BROKEN INPUT"));

        assertTrue(exception.getMessage().contains("Lealone execution failed"));
        assertEquals(InteractiveSessionState.ERROR, session.state());
        assertThrows(IllegalStateException.class, () -> session.submit("SHOW SERVICES"));
        assertEquals(List.of("before failure"), session.drainOutput());
        assertTrue(session.drainOutput().isEmpty());
    }

    @Test
    void startFailureTransitionsToError() {
        RecordingFactory factory = new RecordingFactory();
        factory.openFailure = new RuntimeException("cannot connect");
        LealoneAgentAdapter session = new LealoneAgentAdapter(factory);

        IllegalStateException exception = assertThrows(IllegalStateException.class, session::start);

        assertTrue(exception.getMessage().contains("Failed to start"));
        assertEquals(InteractiveSessionState.ERROR, session.state());
    }

    private static final class RecordingFactory
            implements LealoneAgentAdapter.LealoneExecutionSessionFactory {

        private final Queue<List<String>> nextOutputs = new ArrayDeque<>();
        private RuntimeException openFailure;
        private RecordingExecutionSession openedSession;

        @Override
        public LealoneAgentAdapter.LealoneExecutionSession open() {
            if (openFailure != null) {
                throw openFailure;
            }
            openedSession = new RecordingExecutionSession(nextOutputs);
            return openedSession;
        }
    }

    private static final class RecordingExecutionSession
            implements LealoneAgentAdapter.LealoneExecutionSession {

        private final List<String> inputs = new ArrayList<>();
        private final Queue<List<String>> nextOutputs;
        private RuntimeException failure;
        private boolean closed;
        private int closeCount;

        private RecordingExecutionSession(Queue<List<String>> nextOutputs) {
            this.nextOutputs = nextOutputs;
        }

        @Override
        public List<String> execute(String input) {
            if (failure != null) {
                throw failure;
            }
            inputs.add(input);
            List<String> output = nextOutputs.poll();
            if (output == null) {
                return List.of();
            }
            return output;
        }

        @Override
        public void close() {
            if (!closed) {
                closeCount++;
            }
            closed = true;
        }
    }
}
