package org.specdriven.agent.agent;

import org.specdriven.agent.hook.ToolExecutionHook;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;
import org.specdriven.agent.question.Answer;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.question.QuestionStatus;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolResult;
import org.specdriven.agent.permission.DefaultPermissionProvider;
import org.specdriven.agent.permission.LealonePolicyStore;
import org.specdriven.agent.permission.PermissionProvider;
import org.specdriven.agent.permission.PolicyStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Default implementation of the orchestrator loop:
 * receive → think → act → observe.
 *
 * <p>ToolCalls are executed sequentially in list order so that earlier
 * results are visible to later calls via the Conversation.
 * Tool execution errors are fed back to the LLM as ToolMessages
 * rather than terminating the loop.
 */
public class DefaultOrchestrator implements Orchestrator {

    static final String QUESTION_TOOL_NAME = "__ask_question__";
    private static final long QUESTION_POLL_INTERVAL_MILLIS = 50L;

    private final OrchestratorConfig config;
    private final AgentStateAccessor stateAccessor;
    private final Supplier<PolicyStore> policyStoreFactory;
    private volatile PolicyStore policyStore;

    /**
     * Functional interface to check the agent's current state without
     * coupling the orchestrator to a concrete Agent implementation.
     */
    @FunctionalInterface
    public interface AgentStateAccessor {
        AgentState getState();

        default void transitionTo(AgentState target) {
            throw new IllegalStateException("state transitions are not available");
        }
    }

    public DefaultOrchestrator(OrchestratorConfig config, AgentStateAccessor stateAccessor) {
        this(config, stateAccessor, () -> new LealonePolicyStore("jdbc:lealone:embed:agent_db"));
    }

    DefaultOrchestrator(OrchestratorConfig config,
                        AgentStateAccessor stateAccessor,
                        Supplier<PolicyStore> policyStoreFactory) {
        this.config = config;
        this.stateAccessor = stateAccessor;
        this.policyStoreFactory = policyStoreFactory;
    }

    @Override
    public void run(AgentContext context, LlmClient llmClient) {
        if (llmClient == null) {
            return;
        }
        Conversation conversation = context.conversation();
        if (conversation == null) {
            return;
        }

        int turn = 0;
        outer:
        while (turn < config.maxTurns() && stateAccessor.getState() == AgentState.RUNNING) {
            turn++;

            // think — call LLM with full conversation history
            LlmResponse response = llmClient.chat(conversation.history());

            // record the assistant reply
            if (response instanceof LlmResponse.TextResponse text) {
                conversation.append(new AssistantMessage(text.content(), System.currentTimeMillis()));
                return; // no more tool calls — loop ends
            }

            if (response instanceof LlmResponse.ToolCallResponse toolCalls) {
                // record the assistant's tool-call intent as an assistant message
                conversation.append(new AssistantMessage(
                        toolCalls.toString(), System.currentTimeMillis()));

                // act — execute each tool call sequentially
                for (ToolCall call : toolCalls.toolCalls()) {
                    if (stateAccessor.getState() != AgentState.RUNNING) {
                        return;
                    }
                    if (QUESTION_TOOL_NAME.equals(call.toolName())) {
                        QuestionWaitOutcome outcome = handleQuestionToolCall(call, context, conversation);
                        if (outcome == QuestionWaitOutcome.END_RUN) {
                            return;
                        }
                        if (outcome == QuestionWaitOutcome.RESUME_NEXT_TURN) {
                            continue outer;
                        }
                        continue;
                    }
                    executeToolCall(call, context, conversation);
                }
                // observe — loop continues, LLM sees all tool results
            }
        }
    }

    private QuestionWaitOutcome handleQuestionToolCall(ToolCall call, AgentContext context, Conversation conversation) {
        QuestionRuntime questionRuntime = questionRuntime(context);
        if (questionRuntime == null) {
            appendQuestionError(conversation, call, "question runtime is not configured");
            return QuestionWaitOutcome.CONTINUE_CURRENT_TURN;
        }

        Question waitingQuestion = null;
        try {
            waitingQuestion = beginWaitingQuestion(call, context, questionRuntime, conversation);
            stateAccessor.transitionTo(AgentState.PAUSED);
            return waitForAnswer(waitingQuestion, questionRuntime, conversation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (waitingQuestion != null) {
                try {
                    questionRuntime.closeQuestion(waitingQuestion);
                } catch (IllegalStateException ignored) {
                    // Best effort: the question may already be expired or closed.
                }
            }
            appendQuestionError(conversation, call, e.getMessage());
            return QuestionWaitOutcome.CONTINUE_CURRENT_TURN;
        }
    }

    private Question beginWaitingQuestion(ToolCall call,
                                          AgentContext context,
                                          QuestionRuntime questionRuntime,
                                          Conversation conversation) {
        Map<String, Object> parameters = call.parameters();
        DeliveryMode deliveryMode = parseDeliveryMode(parameters.get("deliveryMode"));
        if (deliveryMode == DeliveryMode.AUTO_AI_REPLY) {
            throw new IllegalArgumentException("AUTO_AI_REPLY is handled by answer-agent-runtime, not pause/wait");
        }

        String questionId = stringParameter(parameters, "questionId");
        if (questionId == null || questionId.isBlank()) {
            questionId = UUID.randomUUID().toString();
        }
        String questionText = requiredParameter(parameters, "question");
        String impact = requiredParameter(parameters, "impact");
        String recommendation = requiredParameter(parameters, "recommendation");
        QuestionCategory category = parseQuestionCategory(parameters.get("category"));

        Question openQuestion = new Question(
                questionId,
                context.sessionId(),
                questionText,
                impact,
                recommendation,
                QuestionStatus.OPEN,
                category,
                deliveryMode);
        Question waitingQuestion = new Question(
                openQuestion.questionId(),
                openQuestion.sessionId(),
                openQuestion.question(),
                openQuestion.impact(),
                openQuestion.recommendation(),
                QuestionStatus.WAITING_FOR_ANSWER,
                openQuestion.category(),
                openQuestion.deliveryMode());
        questionRuntime.beginWaitingQuestion(waitingQuestion);
        conversation.append(new SystemMessage(formatWaitingQuestion(waitingQuestion), System.currentTimeMillis()));
        return waitingQuestion;
    }

    private QuestionWaitOutcome waitForAnswer(Question waitingQuestion,
                                              QuestionRuntime questionRuntime,
                                              Conversation conversation) {
        long deadline = System.currentTimeMillis() + (config.questionTimeoutSeconds() * 1000L);

        try {
            while (stateAccessor.getState() == AgentState.PAUSED) {
                long remainingMillis = deadline - System.currentTimeMillis();
                if (remainingMillis <= 0L) {
                    questionRuntime.expireQuestion(waitingQuestion);
                    return QuestionWaitOutcome.END_RUN;
                }

                long pollMillis = Math.min(remainingMillis, QUESTION_POLL_INTERVAL_MILLIS);
                Optional<Answer> answer = questionRuntime.pollAnswer(
                        waitingQuestion.sessionId(),
                        waitingQuestion.questionId(),
                        pollMillis);
                if (answer.isPresent()) {
                    questionRuntime.acceptAnswer(waitingQuestion, answer.get());
                    conversation.append(new SystemMessage(
                            formatAcceptedAnswer(waitingQuestion, answer.get()),
                            System.currentTimeMillis()));
                    stateAccessor.transitionTo(AgentState.RUNNING);
                    return QuestionWaitOutcome.RESUME_NEXT_TURN;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        questionRuntime.closeQuestion(waitingQuestion);
        return QuestionWaitOutcome.END_RUN;
    }

    private void executeToolCall(ToolCall call, AgentContext context, Conversation conversation) {
        Map<String, Tool> registry = context.toolRegistry();
        Tool tool = registry != null ? registry.get(call.toolName()) : null;

        String resultContent;
        if (tool == null) {
            resultContent = "Error: tool not found: " + call.toolName();
        } else {
            try {
                ToolContext toolCtx = new SimpleToolContext(
                        context.config().getOrDefault("workDir", "."),
                        new LazyPermissionProvider(context.config().getOrDefault("workDir", ".")),
                        Collections.emptyMap());
                ToolInput input = new ToolInput(call.parameters());

                // Run beforeExecute hooks
                ToolResult hookResult = runBeforeHooks(tool, input, toolCtx);
                ToolResult result;
                if (hookResult != null) {
                    result = hookResult;
                } else {
                    result = tool.execute(input, toolCtx);
                    runAfterHooks(tool, input, result);
                }

                if (result instanceof ToolResult.Success success) {
                    resultContent = success.output();
                } else if (result instanceof ToolResult.Error err) {
                    resultContent = "Error: " + err.message();
                } else {
                    resultContent = "Error: unknown ToolResult type";
                }
            } catch (Exception e) {
                resultContent = "Error: " + e.getMessage();
            }
        }

        conversation.append(new ToolMessage(resultContent, System.currentTimeMillis(), call.toolName(), call.callId()));
    }

    private QuestionRuntime questionRuntime(AgentContext context) {
        if (context instanceof SimpleAgentContext simpleAgentContext) {
            return simpleAgentContext.questionRuntime();
        }
        return null;
    }

    private void appendQuestionError(Conversation conversation, ToolCall call, String message) {
        conversation.append(new ToolMessage(
                "Error: " + message,
                System.currentTimeMillis(),
                call.toolName(),
                call.callId()));
    }

    private static String requiredParameter(Map<String, Object> parameters, String key) {
        String value = stringParameter(parameters, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static String stringParameter(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static DeliveryMode parseDeliveryMode(Object rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException("deliveryMode must not be blank");
        }
        try {
            return DeliveryMode.valueOf(String.valueOf(rawValue));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported deliveryMode: " + rawValue);
        }
    }

    private static QuestionCategory parseQuestionCategory(Object rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException("category must not be blank");
        }
        try {
            return QuestionCategory.valueOf(String.valueOf(rawValue));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported category: " + rawValue);
        }
    }

    private static String formatWaitingQuestion(Question question) {
        return "Waiting question [" + question.questionId() + "]\n"
                + "Question: " + question.question() + "\n"
                + "Impact: " + question.impact() + "\n"
                + "Recommendation: " + question.recommendation() + "\n"
                + "Category: " + question.category().name() + "\n"
                + "DeliveryMode: " + question.deliveryMode().name();
    }

    private static String formatAcceptedAnswer(Question question, Answer answer) {
        return "Accepted answer for question [" + question.questionId() + "]\n"
                + "Content: " + answer.content() + "\n"
                + "Basis: " + answer.basisSummary() + "\n"
                + "Source: " + answer.source().name() + " (" + answer.sourceRef() + ")\n"
                + "Decision: " + answer.decision().name();
    }

    private ToolResult runBeforeHooks(Tool tool, ToolInput input, ToolContext toolCtx) {
        List<ToolExecutionHook> hooks = config.hooks();
        for (ToolExecutionHook hook : hooks) {
            ToolResult result = hook.beforeExecute(tool, input, toolCtx);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private void runAfterHooks(Tool tool, ToolInput input, ToolResult result) {
        List<ToolExecutionHook> hooks = config.hooks();
        for (ToolExecutionHook hook : hooks) {
            hook.afterExecute(tool, input, result);
        }
    }

    private PolicyStore policyStore() {
        PolicyStore local = policyStore;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (policyStore == null) {
                policyStore = policyStoreFactory.get();
            }
            return policyStore;
        }
    }

    /**
     * Minimal ToolContext implementation for orchestrator use.
     */
    private record SimpleToolContext(
            String workDir,
            PermissionProvider permissionProvider,
            Map<String, String> env
    ) implements ToolContext {}

    private final class LazyPermissionProvider implements PermissionProvider {
        private final String workDir;
        private volatile PermissionProvider delegate;

        private LazyPermissionProvider(String workDir) {
            this.workDir = workDir;
        }

        @Override
        public PermissionDecision check(Permission permission, PermissionContext context) {
            return delegate().check(permission, context);
        }

        @Override
        public void grant(Permission permission, PermissionContext context) {
            delegate().grant(permission, context);
        }

        @Override
        public void revoke(Permission permission, PermissionContext context) {
            delegate().revoke(permission, context);
        }

        private PermissionProvider delegate() {
            PermissionProvider local = delegate;
            if (local != null) {
                return local;
            }
            synchronized (this) {
                if (delegate == null) {
                    delegate = new DefaultPermissionProvider(workDir, policyStore());
                }
                return delegate;
            }
        }
    }

    private enum QuestionWaitOutcome {
        CONTINUE_CURRENT_TURN,
        RESUME_NEXT_TURN,
        END_RUN
    }
}
