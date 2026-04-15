package org.specdriven.agent.interactive;

import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionDecision;
import org.specdriven.agent.question.QuestionRuntime;

import java.util.Objects;

/**
 * Dispatches parsed interactive commands to the appropriate subsystem.
 *
 * <p>Answer commands are submitted via {@link QuestionRuntime}.
 * Show/Help/Unknown commands produce output through the session's output buffer.
 * Exit commands close the session.
 */
public final class InteractiveCommandHandler {

    private static final String HELP_TEXT = String.join("\n",
            "Available commands:",
            "  ANSWER <text>  - Submit an answer to the waiting question",
            "  YES / Y / OK / CONFIRM  - Affirmative answer",
            "  NO / N / DENY / REJECT  - Negative answer",
            "  SHOW SERVICES  - Show registered services",
            "  SHOW STATUS    - Show current loop and question status",
            "  SHOW ROADMAP   - Show roadmap progress summary",
            "  HELP           - Show this help message",
            "  EXIT / QUIT    - Exit the interactive session"
    );

    private final QuestionRuntime questionRuntime;
    private final InMemoryInteractiveSession session;

    public InteractiveCommandHandler(QuestionRuntime questionRuntime, InMemoryInteractiveSession session) {
        this.questionRuntime = Objects.requireNonNull(questionRuntime, "questionRuntime");
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * Handles a parsed command in the context of a waiting question.
     *
     * @param command         the parsed command (never null)
     * @param waitingQuestion the waiting question (may be null for non-answer commands)
     */
    public void handle(ParsedCommand command, Question waitingQuestion) {
        Objects.requireNonNull(command, "command");

        switch (command) {
            case AnswerCommand ans -> handleAnswer(ans, waitingQuestion);
            case ShowCommand show -> handleShow(show);
            case HelpCommand help -> handleHelp();
            case ExitCommand exit -> handleExit();
            case UnknownCommand unknown -> handleUnknown(unknown);
        }
    }

    private void handleAnswer(AnswerCommand command, Question waitingQuestion) {
        if (waitingQuestion == null) {
            session.queueOutput("No waiting question to answer.");
            return;
        }
        Answer answer = new Answer(
                command.answerText(),
                "Interactive human reply",
                "InteractiveCommandHandler",
                AnswerSource.HUMAN_INLINE,
                1.0,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Interactive session human reply",
                System.currentTimeMillis()
        );
        questionRuntime.submitAnswer(waitingQuestion.sessionId(), waitingQuestion.questionId(), answer);
        session.queueOutput("Answer submitted.");
    }

    private void handleShow(ShowCommand command) {
        String output = switch (command.showType()) {
            case SERVICES -> formatServices();
            case STATUS -> formatStatus();
            case ROADMAP -> formatRoadmap();
        };
        session.queueOutput(output);
    }

    private void handleHelp() {
        session.queueOutput(HELP_TEXT);
    }

    private void handleExit() {
        session.close();
    }

    private void handleUnknown(UnknownCommand command) {
        session.queueOutput("Unknown command: " + command.originalInput() + ". Type HELP for available commands.");
    }

    private String formatServices() {
        return "Services: (not yet connected to service registry)";
    }

    private String formatStatus() {
        return "Status: (not yet connected to loop state)";
    }

    private String formatRoadmap() {
        return "Roadmap: (not yet connected to roadmap store)";
    }

    /**
     * Enqueues an error message into the session output buffer.
     * Used by decorators to surface parse errors without throwing.
     */
    public void enqueueError(String message) {
        session.queueOutput("Error: " + message);
    }
}
