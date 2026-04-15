package org.specdriven.agent.interactive;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.loop.LoopCandidate;
import org.specdriven.agent.loop.SequentialMilestoneScheduler;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.AnswerSource;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionDecision;
import org.specdriven.agent.question.QuestionRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

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
            "  SHOW SERVICES  - Show paused-session capabilities",
            "  SHOW STATUS    - Show current interactive and question status",
            "  SHOW ROADMAP   - Show roadmap progress summary",
            "  HELP           - Show this help message",
            "  EXIT / QUIT    - Exit the interactive session"
    );
    private static final Path DEFAULT_ROADMAP_DIR = Path.of(".spec-driven", "roadmap");

    private final QuestionRuntime questionRuntime;
    private final InMemoryInteractiveSession session;
    private final Path roadmapDir;

    public InteractiveCommandHandler(QuestionRuntime questionRuntime, InMemoryInteractiveSession session) {
        this(questionRuntime, session, DEFAULT_ROADMAP_DIR);
    }

    InteractiveCommandHandler(QuestionRuntime questionRuntime, InMemoryInteractiveSession session, Path roadmapDir) {
        this.questionRuntime = Objects.requireNonNull(questionRuntime, "questionRuntime");
        this.session = Objects.requireNonNull(session, "session");
        this.roadmapDir = Objects.requireNonNull(roadmapDir, "roadmapDir");
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
            case ShowCommand show -> handleShow(show, waitingQuestion);
            case HelpCommand help -> handleHelp(help, waitingQuestion);
            case ExitCommand exit -> handleExit(exit, waitingQuestion);
            case UnknownCommand unknown -> handleUnknown(unknown, waitingQuestion);
        }
    }

    private void handleAnswer(AnswerCommand command, Question waitingQuestion) {
        if (waitingQuestion == null) {
            session.queueOutput("No waiting question to answer.");
            publishAudit(command, null, "rejected_no_waiting_question", null);
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
        publishAudit(command, waitingQuestion, "submitted_answer", metadata ->
                metadata.put("answerLength", command.answerText().length()));
    }

    private void handleShow(ShowCommand command, Question waitingQuestion) {
        String output = switch (command.showType()) {
            case SERVICES -> formatServices(waitingQuestion);
            case STATUS -> formatStatus(waitingQuestion);
            case ROADMAP -> formatRoadmap();
        };
        session.queueOutput(output);
        publishAudit(command, waitingQuestion, "showed_information",
                metadata -> metadata.put("scope", command.showType().name()));
    }

    private void handleHelp(HelpCommand command, Question waitingQuestion) {
        session.queueOutput(HELP_TEXT);
        publishAudit(command, waitingQuestion, "showed_help", null);
    }

    private void handleExit(ExitCommand command, Question waitingQuestion) {
        session.close();
        publishAudit(command, waitingQuestion, "closed_session", null);
    }

    private void handleUnknown(UnknownCommand command, Question waitingQuestion) {
        session.queueOutput("Unknown command: " + command.originalInput() + ". Type HELP for available commands.");
        publishAudit(command, waitingQuestion, "rejected_unknown", null);
    }

    private String formatServices(Question waitingQuestion) {
        Optional<Question> currentQuestion = resolveCurrentQuestion(waitingQuestion);
        StringBuilder sb = new StringBuilder("Paused-session capabilities\n");
        sb.append("- Interactive answers: ANSWER <text>, YES/NO shortcuts\n");
        sb.append("- Waiting-question inspection: SHOW STATUS\n");
        sb.append("- Roadmap progress inspection: SHOW ROADMAP\n");
        sb.append("- Session termination: EXIT / QUIT");
        currentQuestion.ifPresent(question -> {
            sb.append("\n- Current question: ")
                    .append(question.questionId())
                    .append(" (")
                    .append(question.category().name())
                    .append(")");
        });
        return sb.toString();
    }

    private String formatStatus(Question waitingQuestion) {
        Optional<Question> currentQuestion = resolveCurrentQuestion(waitingQuestion);
        StringBuilder sb = new StringBuilder("Interactive status\n");
        sb.append("- sessionId: ").append(session.sessionId()).append('\n');
        sb.append("- sessionState: ").append(session.state().name()).append('\n');
        sb.append("- waitingQuestionRegistered: ").append(currentQuestion.isPresent());
        if (currentQuestion.isPresent()) {
            Question question = currentQuestion.get();
            sb.append('\n').append("- questionId: ").append(question.questionId());
            sb.append('\n').append("- questionStatus: ").append(question.status().name());
            sb.append('\n').append("- questionCategory: ").append(question.category().name());
            sb.append('\n').append("- deliveryMode: ").append(question.deliveryMode().name());
            sb.append('\n').append("- prompt: ").append(question.question());
            sb.append('\n').append("- loopHandoff: waiting for human input");
        }
        return sb.toString();
    }

    private String formatRoadmap() {
        if (!Files.isDirectory(roadmapDir)) {
            return "Roadmap status unavailable: missing directory " + roadmapDir.toAbsolutePath();
        }

        SequentialMilestoneScheduler.RoadmapSummary summary =
                SequentialMilestoneScheduler.summarizeRoadmap(roadmapDir);
        StringBuilder sb = new StringBuilder("Roadmap progress\n");
        sb.append("- totalMilestones: ").append(summary.totalMilestones()).append('\n');
        sb.append("- completeMilestones: ").append(summary.completeMilestones()).append('\n');
        sb.append("- activeMilestones: ").append(summary.activeMilestones()).append('\n');
        sb.append("- plannedChangesRemaining: ").append(summary.plannedChanges()).append('\n');
        sb.append("- completedChanges: ").append(summary.completeChanges());
        summary.nextPlannedChange().ifPresent(candidate -> appendNextPlannedChange(sb, candidate));
        return sb.toString();
    }

    /**
     * Enqueues an error message into the session output buffer.
     * Used by decorators to surface parse errors without throwing.
     */
    public void enqueueError(String message) {
        session.queueOutput("Error: " + message);
    }

    private void appendNextPlannedChange(StringBuilder sb, LoopCandidate candidate) {
        sb.append('\n').append("- nextPlannedChange: ").append(candidate.changeName());
        sb.append('\n').append("- nextMilestone: ").append(candidate.milestoneFile());
        if (!candidate.plannedChangeSummary().isBlank()) {
            sb.append('\n').append("- nextSummary: ").append(candidate.plannedChangeSummary());
        }
    }

    private Optional<Question> resolveCurrentQuestion(Question waitingQuestion) {
        String sessionId = waitingQuestion != null ? waitingQuestion.sessionId() : session.sessionId();
        return questionRuntime.pendingQuestion(sessionId);
    }

    private void publishAudit(ParsedCommand command,
                              Question waitingQuestion,
                              String outcome,
                              java.util.function.Consumer<LinkedHashMap<String, Object>> metadataCustomizer) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", session.sessionId());
        metadata.put("commandCategory", commandCategory(command));
        metadata.put("commandType", commandType(command));
        metadata.put("rawInput", command.originalInput());
        metadata.put("outcome", outcome);
        if (waitingQuestion != null) {
            metadata.put("questionId", waitingQuestion.questionId());
        }
        if (metadataCustomizer != null) {
            metadataCustomizer.accept(metadata);
        }
        questionRuntime.eventBus().publish(new Event(
                EventType.INTERACTIVE_COMMAND_HANDLED,
                System.currentTimeMillis(),
                session.sessionId(),
                metadata
        ));
    }

    private String commandCategory(ParsedCommand command) {
        return switch (command) {
            case AnswerCommand ignored -> "ANSWER";
            case ShowCommand ignored -> "SHOW";
            case HelpCommand ignored -> "HELP";
            case ExitCommand ignored -> "EXIT";
            case UnknownCommand ignored -> "UNKNOWN";
        };
    }

    private String commandType(ParsedCommand command) {
        return switch (command) {
            case AnswerCommand ignored -> "ANSWER";
            case ShowCommand show -> show.showType().name();
            case HelpCommand ignored -> "HELP";
            case ExitCommand ignored -> "EXIT";
            case UnknownCommand ignored -> "UNKNOWN";
        };
    }
}
