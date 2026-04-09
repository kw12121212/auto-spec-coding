package org.specdriven.agent.agent;

import java.util.Map;
import java.util.Optional;
import org.specdriven.agent.tool.ProcessManager;
import org.specdriven.agent.tool.Tool;

/**
 * Execution context provided to an agent.
 */
public interface AgentContext {

    /**
     * Returns the unique session identifier for this agent invocation.
     */
    String sessionId();

    /**
     * Returns the agent configuration as key-value pairs.
     */
    Map<String, String> config();

    /**
     * Returns the registry of tools available to this agent.
     * May return null if no tools have been registered yet.
     */
    Map<String, Tool> toolRegistry();

    /**
     * Returns the conversation context for this agent invocation.
     * Default implementation returns null for backward compatibility.
     */
    default Conversation conversation() {
        return null;
    }

    /**
     * Returns the process manager for background process lifecycle management.
     * Default implementation returns empty for backward compatibility.
     */
    default Optional<ProcessManager> processManager() {
        return Optional.empty();
    }
}
