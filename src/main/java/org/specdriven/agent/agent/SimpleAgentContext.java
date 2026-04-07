package org.specdriven.agent.agent;

import java.util.Map;
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

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation) {
        this(sessionId, config, toolRegistry, conversation, null);
    }

    public SimpleAgentContext(String sessionId,
                              Map<String, String> config,
                              Map<String, Tool> toolRegistry,
                              Conversation conversation,
                              SessionStore sessionStore) {
        this.sessionId = sessionId;
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.conversation = conversation;
        this.sessionStore = sessionStore;
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
}
