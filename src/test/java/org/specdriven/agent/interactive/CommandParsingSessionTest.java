package org.specdriven.agent.interactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandParsingSessionTest {

    private InMemoryInteractiveSession delegate;
    private CommandParsingSession session;
    private QuestionRuntime runtime;
    private Question waitingQuestion;

    @BeforeEach
    void setUp() {
        delegate = new InMemoryInteractiveSession();
        runtime = new QuestionRuntime(new StubEventBus());
        InteractiveCommandHandler handler = new InteractiveCommandHandler(runtime, delegate);
        CommandParser parser = new DefaultCommandParser();

        waitingQuestion = new Question(
                "q1", delegate.sessionId(), "Proceed?",
                "Impact", "Recommendation",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
        runtime.beginWaitingQuestion(waitingQuestion);

        session = new CommandParsingSession(delegate, parser, handler, waitingQuestion);
    }

    // --- Lifecycle delegation ---

    @Test
    void startDelegatesToUnderlyingSession() {
        assertEquals(InteractiveSessionState.NEW, session.state());
        session.start();
        assertEquals(InteractiveSessionState.ACTIVE, session.state());
    }

    @Test
    void sessionIdDelegates() {
        assertEquals(delegate.sessionId(), session.sessionId());
    }

    @Test
    void closeDelegates() {
        session.start();
        session.close();
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void drainOutputDelegates() {
        session.start();
        session.submit("HELP");
        List<String> output = session.drainOutput();
        assertFalse(output.isEmpty());
        assertTrue(output.get(0).contains("Available commands"));
    }

    // --- Parse + dispatch ---

    @Test
    void helpParsesAndDispatches() {
        session.start();
        session.submit("HELP");
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("Available commands"));
    }

    @Test
    void exitParsesAndCloses() {
        session.start();
        session.submit("EXIT");
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    @Test
    void showParsesAndProducesOutput() {
        session.start();
        session.submit("SHOW STATUS");
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
    }

    @Test
    void answerParsesAndSubmits() throws Exception {
        session.start();
        session.submit("yes");
        var answer = runtime.pollAnswer(delegate.sessionId(), "q1", 100);
        assertTrue(answer.isPresent());
        assertEquals(AnswerSource.HUMAN_INLINE, answer.get().source());
    }

    @Test
    void unknownProducesGuidanceOutput() {
        session.start();
        session.submit("something weird");
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("Unknown command"));
    }

    // --- Parse error produces output, not exception ---

    @Test
    void blankInputProducesErrorOutputNotException() {
        session.start();
        // Blank input would throw from parser, but CommandParsingSession catches it
        assertDoesNotThrow(() -> session.submit(""));
        List<String> output = session.drainOutput();
        assertFalse(output.isEmpty());
        assertTrue(output.get(0).contains("Error"));
    }

    @Test
    void nullInputProducesErrorOutputNotException() {
        session.start();
        assertDoesNotThrow(() -> session.submit(null));
        List<String> output = session.drainOutput();
        assertFalse(output.isEmpty());
        assertTrue(output.get(0).contains("Error"));
    }

    // --- Stub EventBus ---

    private static class StubEventBus implements EventBus {
        @Override
        public void publish(Event event) {}
        @Override
        public void subscribe(EventType type, java.util.function.Consumer<Event> listener) {}
        @Override
        public void unsubscribe(EventType type, java.util.function.Consumer<Event> listener) {}
    }
}
