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
    VAULT_SECRET_CREATED,
    VAULT_SECRET_DELETED,
    ERROR
}
