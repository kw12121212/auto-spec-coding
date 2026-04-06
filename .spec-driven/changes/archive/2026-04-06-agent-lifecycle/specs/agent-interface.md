# Agent Interface Spec (Delta)

## ADDED Requirements

### Requirement: Agent state transitions

- MUST enforce the following valid transitions: IDLE→RUNNING (via start), RUNNING→STOPPED (via stop), RUNNING→PAUSED (reserved for orchestrator), RUNNING→ERROR (on uncaught exception in execute), PAUSED→RUNNING (reserved), PAUSED→STOPPED (via stop), ERROR→STOPPED (via stop)
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- MUST treat STOPPED as a terminal state — no transition away from STOPPED is allowed

### Requirement: Agent init behavior

- MUST accept configuration via init(Map<String, String> config) and store it for later access
- MUST transition to IDLE state after successful init
- MUST NOT allow init to be called more than once — second call MUST throw IllegalStateException

### Requirement: Agent start behavior

- MUST transition from IDLE to RUNNING when start() is called
- MUST throw IllegalStateException if start() is called in any state other than IDLE

### Requirement: Agent execute behavior

- MUST only be callable when the agent is in RUNNING state
- MUST throw IllegalStateException if called in any state other than RUNNING
- MUST automatically transition to ERROR state if execute throws an uncaught exception
- MUST accept an AgentContext parameter providing session ID, config, and tool registry

### Requirement: Agent stop behavior

- MUST transition to STOPPED when called from RUNNING, PAUSED, or ERROR states
- MUST throw IllegalStateException if called from IDLE or STOPPED state

### Requirement: Agent close behavior

- MUST be callable from any state
- MUST release all held resources
- MUST result in STOPPED state after close completes

### Requirement: DefaultAgent implementation

- MUST be a concrete class implementing the Agent interface in the org.specdriven.agent.agent package
- MUST follow the Lealone plugin lifecycle pattern (init/start/stop/close) by method naming convention
