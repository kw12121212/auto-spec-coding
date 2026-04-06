# Agent Interface Spec

## ADDED Requirements

### Requirement: Agent lifecycle

- MUST follow a lifecycle: init → start → (execute) → stop → close
- MUST track its state via `getState()` returning `AgentState`
- SHOULD integrate with Lealone plugin lifecycle conventions (init/start/stop/close) by pattern, not inheritance

### Requirement: AgentState enum

- MUST define states: IDLE, RUNNING, PAUSED, STOPPED, ERROR
- Each state MUST be independently testable

### Requirement: AgentContext

- MUST provide a session identifier
- MUST provide access to configuration
- SHOULD provide access to a tool registry
