package org.specdriven.agent.agent;

import java.util.Map;
import java.util.Optional;
import org.specdriven.agent.answer.AnswerAgentRuntime;
import org.specdriven.agent.question.QuestionRuntime;
import org.specdriven.agent.tool.ProcessManager;
import org.specdriven.agent.tool.Tool;

/**
 * Default AgentContext implementation combining all context fields.
 */
public class SimpleAgentContext implements AgentContext {

    private final String sessionId;
    private final Map<String, String> config;
    private final Map<String, Tool> toolRegistry;
    private final Conversation conversation;
    private final SessionStore sessionStore;
    private final ProcessManager processManager;
    private final QuestionRuntime questionRuntime;
    private final AnswerAgentRuntime answerAgentRuntime;

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation) {
        this(sessionId, config, toolRegistry, conversation, null, null);
    }

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation,
                              SessionStore sessionStore) {
        this(sessionId, config, toolRegistry, conversation, sessionStore, null);
    }

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation,
                              SessionStore sessionStore,
                              ProcessManager processManager) {
        this(sessionId, config, toolRegistry, conversation, sessionStore, processManager, null);
    }

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation,
                              SessionStore sessionStore,
                              ProcessManager processManager,
                              QuestionRuntime questionRuntime) {
        this(sessionId, config, toolRegistry, conversation, sessionStore, processManager, questionRuntime, null);
    }

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation,
                              SessionStore sessionStore,
                              ProcessManager processManager,
                              QuestionRuntime questionRuntime,
                              AnswerAgentRuntime answerAgentRuntime) {
        this.sessionId = sessionId;
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.conversation = conversation;
        this.sessionStore = sessionStore;
        this.processManager = processManager;
        this.questionRuntime = questionRuntime;
        this.answerAgentRuntime = answerAgentRuntime;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public Map<String, String> config() {
        return config;
    }

    @Override
    public Map<String, Tool> toolRegistry() {
        return toolRegistry;
    }

    @Override
    public Conversation conversation() {
        return conversation;
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }

    public QuestionRuntime questionRuntime() {
        return questionRuntime;
    }

    public AnswerAgentRuntime answerAgentRuntime() {
        return answerAgentRuntime;
    }

    @Override
    public Optional<ProcessManager> processManager() {
        return Optional.ofNullable(processManager);
    }
}
