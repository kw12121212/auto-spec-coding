package org.specdriven.agent.interactive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveCommandHandlerTest {

    @TempDir
    Path tempDir;

    private InMemoryInteractiveSession session;
    private QuestionRuntime runtime;
    private InteractiveCommandHandler handler;
    private Question waitingQuestion;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() throws IOException {
        session = new InMemoryInteractiveSession();
        session.start(); // ACTIVE
        eventBus = new CapturingEventBus();
        runtime = new QuestionRuntime(eventBus);
        handler = new InteractiveCommandHandler(runtime, session, prepareRoadmap(tempDir));

        waitingQuestion = new Question(
                "q1", session.sessionId(), "Should we proceed?",
                "Build may fail", "Proceed with cached version",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
        runtime.beginWaitingQuestion(waitingQuestion);
        eventBus.clear();
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

    @Test
    void answerPublishesInteractiveAuditEvent() {
        handler.handle(new AnswerCommand("yes", "yes"), waitingQuestion);

        Event event = singleInteractiveAuditEvent();
        assertEquals(EventType.INTERACTIVE_COMMAND_HANDLED, event.type());
        assertEquals(session.sessionId(), event.source());
        assertEquals(session.sessionId(), event.metadata().get("sessionId"));
        assertEquals("ANSWER", event.metadata().get("commandCategory"));
        assertEquals("ANSWER", event.metadata().get("commandType"));
        assertEquals("yes", event.metadata().get("rawInput"));
        assertEquals("submitted_answer", event.metadata().get("outcome"));
        assertEquals("q1", event.metadata().get("questionId"));
    }

    // --- ShowCommand ---

    @Test
    void showServicesProducesPausedSessionCapabilities() {
        handler.handle(new ShowCommand("SHOW SERVICES", ShowType.SERVICES), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
        assertTrue(output.get(0).contains("Paused-session capabilities"));
        assertTrue(output.get(0).contains("SHOW STATUS"));
        assertFalse(output.get(0).contains("not yet connected"));
    }

    @Test
    void showStatusReportsWaitingQuestionContext() {
        handler.handle(new ShowCommand("SHOW STATUS", ShowType.STATUS), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
        assertTrue(output.get(0).contains("waitingQuestionRegistered: true"));
        assertTrue(output.get(0).contains("questionId: q1"));
        assertTrue(output.get(0).contains("loopHandoff: waiting for human input"));
        assertFalse(output.get(0).contains("not yet connected"));
    }

    @Test
    void showStatusDoesNotReportClearedQuestionAsStillWaiting() {
        runtime.closeQuestion(waitingQuestion);

        handler.handle(new ShowCommand("SHOW STATUS", ShowType.STATUS), waitingQuestion);
        List<String> output = session.drainOutput();

        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("waitingQuestionRegistered: false"));
        assertFalse(output.get(0).contains("questionId: q1"));
    }

    @Test
    void showRoadmapReportsOnDiskProgress() {
        handler.handle(new ShowCommand("SHOW ROADMAP", ShowType.ROADMAP), waitingQuestion);
        List<String> output = session.drainOutput();
        assertEquals(1, output.size());
        assertFalse(output.get(0).isBlank());
        assertTrue(output.get(0).contains("Roadmap progress"));
        assertTrue(output.get(0).contains("plannedChangesRemaining: 1"));
        assertTrue(output.get(0).contains("nextPlannedChange: interactive-show-audit"));
        assertFalse(output.get(0).contains("not yet connected"));
    }

    @Test
    void showCommandPublishesInteractiveAuditEvent() {
        handler.handle(new ShowCommand("SHOW STATUS", ShowType.STATUS), waitingQuestion);

        Event event = singleInteractiveAuditEvent();
        assertEquals("SHOW", event.metadata().get("commandCategory"));
        assertEquals("STATUS", event.metadata().get("commandType"));
        assertEquals("SHOW STATUS", event.metadata().get("rawInput"));
        assertEquals("showed_information", event.metadata().get("outcome"));
        assertEquals("STATUS", event.metadata().get("scope"));
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

    @Test
    void unknownPublishesInteractiveAuditEvent() {
        handler.handle(new UnknownCommand("foo"), waitingQuestion);

        Event event = singleInteractiveAuditEvent();
        assertEquals("UNKNOWN", event.metadata().get("commandCategory"));
        assertEquals("UNKNOWN", event.metadata().get("commandType"));
        assertEquals("foo", event.metadata().get("rawInput"));
        assertEquals("rejected_unknown", event.metadata().get("outcome"));
    }

    private Event singleInteractiveAuditEvent() {
        List<Event> events = eventBus.eventsOfType(EventType.INTERACTIVE_COMMAND_HANDLED);
        assertEquals(1, events.size());
        return events.get(0);
    }

    private static Path prepareRoadmap(Path root) throws IOException {
        Path roadmapDir = root.resolve("roadmap");
        Path milestonesDir = roadmapDir.resolve("milestones");
        Files.createDirectories(milestonesDir);
        Files.writeString(roadmapDir.resolve("INDEX.md"),
                "- [m29.md](milestones/m29.md) - M29 - proposed\n");
        Files.writeString(milestonesDir.resolve("m29.md"),
                "# M29\n" +
                        "## Goal\n" +
                        "Interactive workflow\n" +
                        "## Status\n" +
                        "- Declared: proposed\n" +
                        "## Planned Changes\n" +
                        "- `interactive-show-audit` - Declared: planned - finish interactive audit visibility\n");
        return roadmapDir;
    }

    private static class CapturingEventBus implements EventBus {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void publish(Event event) {
            events.add(event);
        }

        @Override
        public void subscribe(EventType type, java.util.function.Consumer<Event> listener) {}

        @Override
        public void unsubscribe(EventType type, java.util.function.Consumer<Event> listener) {}

        List<Event> eventsOfType(EventType type) {
            return events.stream().filter(event -> event.type() == type).toList();
        }

        void clear() {
            events.clear();
        }
    }
}
