package org.specdriven.agent.loop;

import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.agent.Conversation;
import org.specdriven.agent.agent.DefaultOrchestrator;
import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.OrchestratorConfig;
import org.specdriven.agent.agent.SimpleAgentContext;
import org.specdriven.agent.agent.SmartContextInjector;
import org.specdriven.agent.agent.SmartContextInjectorConfig;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline implementation that executes the spec-driven workflow
 * (recommend -> propose -> implement -> verify -> review -> archive) for a single change.
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

    private final SpecDrivenPhaseRunner phaseRunner;

    /**
     * Creates a pipeline backed by an explicit phase runner.
     */
    public SpecDrivenPipeline(SpecDrivenPhaseRunner phaseRunner) {
        this.phaseRunner = Objects.requireNonNull(phaseRunner, "phaseRunner must not be null");
    }

    /**
     * Creates a pipeline with default tools (bash, read, write, edit, glob, grep).
     */
    public SpecDrivenPipeline(Function<Path, LlmClient> llmClientFactory) {
        this(new PromptBackedPhaseRunner(llmClientFactory, DEFAULT_TOOLS));
    }

    /**
     * Creates a pipeline with custom tools.
     */
    public SpecDrivenPipeline(Function<Path, LlmClient> llmClientFactory,
                              Map<String, Tool> toolRegistry) {
        this(new PromptBackedPhaseRunner(llmClientFactory, toolRegistry));
    }

    @Override
    public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
        long startMs = System.currentTimeMillis();
        if (phaseRunner instanceof ExecutionScopedPhaseRunner scopedRunner) {
            scopedRunner.begin(config);
            try {
                return executePhases(candidate, config, skipPhases, startMs);
            } finally {
                scopedRunner.end();
            }
        }
        return executePhases(candidate, config, skipPhases, startMs);
    }

    private IterationResult executePhases(LoopCandidate candidate,
                                          LoopConfig config,
                                          Set<PipelinePhase> skipPhases,
                                          long startMs) {
        long deadlineMs = startMs + config.iterationTimeoutSeconds() * 1000L;
        List<PipelinePhase> completed = new ArrayList<>();
        long tokenUsage = 0;

        for (PipelinePhase phase : PipelinePhase.ordered()) {
            if (skipPhases.contains(phase)) {
                continue;
            }

            if (System.currentTimeMillis() > deadlineMs) {
                return result(IterationStatus.TIMED_OUT,
                        "Timeout exceeded before " + phase.name() + " phase",
                        startMs, completed, tokenUsage, null);
            }

            Consumer<Event> questionListener = event -> {
                throw new QuestionCaptureException(reconstructQuestion(event), 0);
            };
            config.eventBus().subscribe(EventType.QUESTION_CREATED, questionListener);
            try {
                PhaseExecutionResult phaseResult = phaseRunner.run(phase, candidate, config);
                tokenUsage += phaseResult.tokenUsage();
                if (phaseResult.status() == IterationStatus.SUCCESS) {
                    completed.add(phase);
                    continue;
                }
                if (phaseResult.status() == IterationStatus.QUESTIONING) {
                    return result(IterationStatus.QUESTIONING, null, startMs, completed,
                            tokenUsage, phaseResult.question());
                }
                return result(phaseResult.status(), phaseFailureReason(phase, phaseResult),
                        startMs, completed, tokenUsage, null);
            } catch (QuestionCaptureException e) {
                tokenUsage += e.tokenUsage();
                return result(IterationStatus.QUESTIONING, null, startMs, completed,
                        tokenUsage, e.question());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Pipeline phase failed: " + phase.name(), e);
                return result(IterationStatus.FAILED,
                        "Phase " + phase.name() + " failed: " + failureMessage(e),
                        startMs, completed, tokenUsage, null);
            } finally {
                config.eventBus().unsubscribe(EventType.QUESTION_CREATED, questionListener);
            }
        }

        return result(IterationStatus.SUCCESS, null, startMs, completed, tokenUsage, null);
    }

    private static String phaseFailureReason(PipelinePhase phase, PhaseExecutionResult phaseResult) {
        String reason = phaseResult.failureReason();
        if (reason == null || reason.isBlank()) {
            return "Phase " + phase.name() + " returned " + phaseResult.status();
        }
        return reason.contains(phase.name()) ? reason : "Phase " + phase.name() + " failed: " + reason;
    }

    private static String failureMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
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

    private static LlmClient contextOptimizedClient(LlmClient client, LoopConfig config) {
        if (config.contextBudget() == null) {
            return client;
        }
        return new SmartContextInjector(
                client,
                SmartContextInjectorConfig.defaults(config.contextBudget().maxTokens()));
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

    private interface ExecutionScopedPhaseRunner extends SpecDrivenPhaseRunner {
        void begin(LoopConfig config);

        void end();
    }

    private static final class PromptBackedPhaseRunner implements ExecutionScopedPhaseRunner {
        private final Function<Path, LlmClient> llmClientFactory;
        private final Map<String, Tool> toolRegistry;
        private final ThreadLocal<TokenAccumulator> activeTokenAccumulator = new ThreadLocal<>();

        private PromptBackedPhaseRunner(Function<Path, LlmClient> llmClientFactory,
                                        Map<String, Tool> toolRegistry) {
            this.llmClientFactory = Objects.requireNonNull(llmClientFactory, "llmClientFactory must not be null");
            this.toolRegistry = Collections.unmodifiableMap(new HashMap<>(
                    Objects.requireNonNull(toolRegistry, "toolRegistry must not be null")));
        }

        @Override
        public void begin(LoopConfig config) {
            LlmClient rawClient = llmClientFactory.apply(config.projectRoot());
            activeTokenAccumulator.set(new TokenAccumulator(rawClient));
        }

        @Override
        public void end() {
            activeTokenAccumulator.remove();
        }

        @Override
        public PhaseExecutionResult run(PipelinePhase phase, LoopCandidate candidate, LoopConfig config) {
            TokenAccumulator tokenAccumulator = activeTokenAccumulator.get();
            boolean localAccumulator = false;
            long beforeTokens = 0;
            try {
                if (tokenAccumulator == null) {
                    LlmClient rawClient = llmClientFactory.apply(config.projectRoot());
                    tokenAccumulator = new TokenAccumulator(rawClient);
                    localAccumulator = true;
                }
                beforeTokens = tokenAccumulator.totalTokens();
                LlmClient phaseClient = contextOptimizedClient(tokenAccumulator, config);
                executePhase(phase, candidate, config, phaseClient);
                return PhaseExecutionResult.success(tokenAccumulator.totalTokens() - beforeTokens);
            } catch (QuestionCaptureException e) {
                long tokens = tokenAccumulator != null ? tokenAccumulator.totalTokens() - beforeTokens : e.tokenUsage();
                throw new QuestionCaptureException(e.question(), tokens);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PhaseExecutionResult.timedOut("Interrupted during " + phase.name() + " phase");
            } catch (Exception e) {
                return PhaseExecutionResult.failed(failureMessage(e));
            } finally {
                if (localAccumulator) {
                    activeTokenAccumulator.remove();
                }
            }
        }

        private void executePhase(PipelinePhase phase,
                                  LoopCandidate candidate,
                                  LoopConfig config,
                                  LlmClient llmClient) throws IOException, InterruptedException {
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
                    new QuestionRuntime(config.eventBus())
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
                    .replace("${plannedChangeSummary}", candidate.plannedChangeSummary())
                    .replace("${projectRoot}", config.projectRoot().toAbsolutePath().toString());
        }

        private String buildUserPrompt(PipelinePhase phase,
                                       LoopCandidate candidate,
                                       LoopConfig config) {
            return "Execute the " + phase.name() + " phase for change '" + candidate.changeName()
                    + "' from milestone '" + candidate.milestoneFile() + "'."
                    + "\nPlanned change summary: " + candidate.plannedChangeSummary()
                    + "\nProject root: " + config.projectRoot().toAbsolutePath();
        }
    }

    private static final class QuestionCaptureException extends RuntimeException {
        private final Question question;
        private final long tokenUsage;

        QuestionCaptureException(Question question, long tokenUsage) {
            super("Question captured: " + (question != null ? question.questionId() : "null"),
                    null, true, false);
            this.question = question;
            this.tokenUsage = tokenUsage;
        }

        Question question() {
            return question;
        }

        long tokenUsage() {
            return tokenUsage;
        }
    }
}
