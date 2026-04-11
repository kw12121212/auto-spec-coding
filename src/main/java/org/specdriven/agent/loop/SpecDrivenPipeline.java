package org.specdriven.agent.loop;

import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.agent.Conversation;
import org.specdriven.agent.agent.DefaultOrchestrator;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.OrchestratorConfig;
import org.specdriven.agent.agent.SimpleAgentContext;
import org.specdriven.agent.agent.SystemMessage;
import org.specdriven.agent.agent.UserMessage;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.question.QuestionStatus;
import org.specdriven.agent.tool.BashTool;
import org.specdriven.agent.tool.EditTool;
import org.specdriven.agent.tool.GlobTool;
import org.specdriven.agent.tool.GrepTool;
import org.specdriven.agent.tool.ReadTool;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.WriteTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline implementation that executes the spec-driven workflow
 * (propose → implement → verify → review → archive) for a single change.
 */
public class SpecDrivenPipeline implements LoopPipeline {

    private static final Logger LOG = Logger.getLogger(SpecDrivenPipeline.class.getName());

    private static final Map<String, Tool> DEFAULT_TOOLS = Map.of(
            "bash", new BashTool(),
            "read", new ReadTool(),
            "write", new WriteTool(),
            "edit", new EditTool(),
            "glob", new GlobTool(),
            "grep", new GrepTool()
    );

    private final Function<Path, LlmClient> llmClientFactory;
    private final Map<String, Tool> toolRegistry;

    /**
     * Creates a pipeline with default tools (bash, read, write, edit, glob, grep).
     */
    public SpecDrivenPipeline(Function<Path, LlmClient> llmClientFactory) {
        this(llmClientFactory, DEFAULT_TOOLS);
    }

    /**
     * Creates a pipeline with custom tools.
     */
    public SpecDrivenPipeline(Function<Path, LlmClient> llmClientFactory,
                              Map<String, Tool> toolRegistry) {
        this.llmClientFactory = llmClientFactory;
        this.toolRegistry = Collections.unmodifiableMap(new HashMap<>(toolRegistry));
    }

    @Override
    public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
        long startMs = System.currentTimeMillis();
        long deadlineMs = startMs + config.iterationTimeoutSeconds() * 1000L;
        List<PipelinePhase> completed = new ArrayList<>();

        try {
            LlmClient rawClient = llmClientFactory.apply(config.projectRoot());
            TokenAccumulator tokenAccumulator = new TokenAccumulator(rawClient);

            for (PipelinePhase phase : PipelinePhase.ordered()) {
                if (skipPhases.contains(phase)) {
                    continue;
                }

                if (System.currentTimeMillis() > deadlineMs) {
                    return result(IterationStatus.TIMED_OUT,
                            "Timeout exceeded before " + phase.name() + " phase",
                            startMs, completed, tokenAccumulator.totalTokens(), null);
                }

                QuestionRuntime questionRuntime = new QuestionRuntime(config.eventBus());
                Consumer<Event> questionListener = event -> {
                    throw new QuestionCaptureException(reconstructQuestion(event));
                };
                config.eventBus().subscribe(EventType.QUESTION_CREATED, questionListener);
                try {
                    executePhase(phase, candidate, config, tokenAccumulator, questionRuntime);
                    completed.add(phase);
                } catch (QuestionCaptureException e) {
                    return result(IterationStatus.QUESTIONING, null, startMs, completed,
                            tokenAccumulator.totalTokens(), e.question());
                } finally {
                    config.eventBus().unsubscribe(EventType.QUESTION_CREATED, questionListener);
                }
            }

            return result(IterationStatus.SUCCESS, null, startMs, completed,
                    tokenAccumulator.totalTokens(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(IterationStatus.TIMED_OUT,
                    "Interrupted during pipeline execution",
                    startMs, completed, 0, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Pipeline execution failed", e);
            return result(IterationStatus.FAILED,
                    e.getMessage() != null ? e.getMessage() : "unknown error",
                    startMs, completed, 0, null);
        }
    }

    private void executePhase(PipelinePhase phase,
                              LoopCandidate candidate,
                              LoopConfig config,
                              LlmClient llmClient,
                              QuestionRuntime questionRuntime) throws IOException, InterruptedException {
        String template = loadTemplate(phase);
        String systemPrompt = substituteVariables(template, candidate, config);
        String userPrompt = buildUserPrompt(phase, candidate, config);

        Conversation conversation = new Conversation();
        conversation.append(new SystemMessage(systemPrompt, System.currentTimeMillis()));
        conversation.append(new UserMessage(userPrompt, System.currentTimeMillis()));

        Map<String, String> contextConfig = new HashMap<>();
        contextConfig.put("workDir", config.projectRoot().toAbsolutePath().toString());
        contextConfig.put("skill_id", "loop-" + phase.name().toLowerCase());

        SimpleAgentContext context = new SimpleAgentContext(
                UUID.randomUUID().toString(),
                contextConfig,
                toolRegistry,
                conversation,
                null,
                null,
                questionRuntime
        );

        OrchestratorConfig orchestratorConfig = OrchestratorConfig.defaults();
        DefaultOrchestrator orchestrator = new DefaultOrchestrator(
                orchestratorConfig, () -> AgentState.RUNNING);
        orchestrator.run(context, llmClient);
    }

    private String loadTemplate(PipelinePhase phase) throws IOException {
        String resourcePath = phase.templateResource();
        try (InputStream is = SpecDrivenPipeline.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Template resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String substituteVariables(String template,
                                       LoopCandidate candidate,
                                       LoopConfig config) {
        return template
                .replace("${changeName}", candidate.changeName())
                .replace("${milestoneGoal}", candidate.milestoneGoal() != null ? candidate.milestoneGoal() : "")
                .replace("${projectRoot}", config.projectRoot().toAbsolutePath().toString());
    }

    private String buildUserPrompt(PipelinePhase phase,
                                   LoopCandidate candidate,
                                   LoopConfig config) {
        return "Execute the " + phase.name() + " phase for change '" + candidate.changeName()
                + "' from milestone '" + candidate.milestoneFile() + "'."
                + "\nProject root: " + config.projectRoot().toAbsolutePath();
    }

    private IterationResult result(IterationStatus status,
                                   String failureReason,
                                   long startMs,
                                   List<PipelinePhase> completed,
                                   long tokenUsage,
                                   Question question) {
        return new IterationResult(status, failureReason,
                System.currentTimeMillis() - startMs, completed, tokenUsage, question);
    }

    private static Question reconstructQuestion(Event event) {
        Map<String, Object> md = event.metadata();
        return new Question(
                (String) md.get("questionId"),
                (String) md.get("sessionId"),
                (String) md.get("question"),
                (String) md.get("impact"),
                (String) md.get("recommendation"),
                QuestionStatus.valueOf((String) md.get("status")),
                QuestionCategory.valueOf((String) md.get("category")),
                DeliveryMode.valueOf((String) md.get("deliveryMode"))
        );
    }

    private static final class QuestionCaptureException extends RuntimeException {
        private final Question question;

        QuestionCaptureException(Question question) {
            super("Question captured: " + (question != null ? question.questionId() : "null"),
                    null, true, false);
            this.question = question;
        }

        Question question() {
            return question;
        }
    }
}
