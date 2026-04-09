package org.specdriven.agent.event;

/**
 * Types of events that can occur in the agent system.
 */
public enum EventType {
    TOOL_EXECUTED,
    AGENT_STATE_CHANGED,
    TASK_CREATED,
    TASK_COMPLETED,
    TEAM_CREATED,
    TEAM_DISSOLVED,
    CRON_TRIGGERED,
    BACKGROUND_TOOL_STARTED,
    BACKGROUND_TOOL_STOPPED,
    SERVER_TOOL_READY,
    SERVER_TOOL_FAILED,
    VAULT_SECRET_CREATED,
    VAULT_SECRET_DELETED,
    ERROR
}
