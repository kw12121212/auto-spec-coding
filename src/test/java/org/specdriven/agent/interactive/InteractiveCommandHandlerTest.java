package org.specdriven.agent.interactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveCommandHandlerTest {

    private InMemoryInteractiveSession session;
    private QuestionRuntime runtime;
    private InteractiveCommandHandler handler;
    private Question waitingQuestion;

    @BeforeEach
    void setUp() {
        session = new InMemoryInteractiveSession();
        session.start(); // ACTIVE
        runtime = new QuestionRuntime(new StubEventBus());
        handler = new InteractiveCommandHandler(runtime, session);

        waitingQuestion = new Question(
                "q1", session.sessionId(), "Should we proceed?",
                "Build may fail", "Proceed with cached version",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
        runtime.beginWaitingQuestion(waitingQuestion);
    }

    // --- AnswerCommand ---

    @Test
    void answerSubmitsToRuntime() throws Exception {
        handler.handle(new AnswerCommand("yes", "yes"), waitingQuestion);

        // Answer was accepted by the runtime
        var answer = runtime.pollAnswer(session.sessionId(), "q1", 100);
        assertTrue(answer.isPresent());
        assertEquals(AnswerSource.HUMAN_INLINE, answer.get().source());
        assertEquals(QuestionDecision.ANSWER_ACCEPTED, answer.get().decision());
        assertEquals(DeliveryMode.PAUSE_WAIT_HUMAN, answer.get().deliveryMode());
        assertEquals(1.0, answer.get().confidence());
        assertEquals("Interactive human reply", answer.get().basisSummary());
        assertEquals("InteractiveCommandHandler", answer.get().sourceRef());
    }

    @Test
    void answerDoesNotCloseSession() {
        handler.handle(new AnswerCommand("yes", "yes"), waitingQuestion);
        assertEquals(InteractiveSessionState.ACTIVE, session.state());
    }

    @Test
    void answerProducesOutputConfirmation() {
        handler.handle(new AnswerCommand("yes", "yes"), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("Answer submitted"));
    }

    @Test
    void answerWithNullQuestionProducesErrorMessage() {
        handler.handle(new AnswerCommand("yes", "yes"), null);
        List<String> output = session.drainOutput();
        assertFalse(output.isEmpty());
        assertTrue(output.get(0).contains("No waiting question"));
    }

    // --- ShowCommand ---

    @Test
    void showServicesProducesOutput() {
        handler.handle(new ShowCommand("SHOW SERVICES", ShowType.SERVICES), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
    }

    @Test
    void showStatusProducesOutput() {
        handler.handle(new ShowCommand("SHOW STATUS", ShowType.STATUS), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
    }

    @Test
    void showRoadmapProducesOutput() {
        handler.handle(new ShowCommand("SHOW ROADMAP", ShowType.ROADMAP), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
    }

    // --- HelpCommand ---

    @Test
    void helpProducesOutput() {
        handler.handle(new HelpCommand("HELP"), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("Available commands"));
        assertTrue(output.get(0).contains("ANSWER"));
        assertTrue(output.get(0).contains("HELP"));
        assertTrue(output.get(0).contains("EXIT"));
    }

    // --- ExitCommand ---

    @Test
    void exitClosesSession() {
        handler.handle(new ExitCommand("exit"), waitingQuestion);
        assertEquals(InteractiveSessionState.CLOSED, session.state());
    }

    // --- UnknownCommand ---

    @Test
    void unknownProducesGuidance() {
        handler.handle(new UnknownCommand("foo bar"), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("Unknown command"));
        assertTrue(output.get(0).contains("HELP"));
    }

    @Test
    void unknownDoesNotCloseSession() {
        handler.handle(new UnknownCommand("foo"), waitingQuestion);
        assertEquals(InteractiveSessionState.ACTIVE, session.state());
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
