# Agent Interface Spec (delta)

## MODIFIED Requirements

### Requirement: Agent state transitions

- MUST enforce the following valid transitions: IDLE→RUNNING (via start), RUNNING→STOPPED (via stop), RUNNING→PAUSED (when orchestrator suspends execution waiting for a question answer), RUNNING→ERROR (on uncaught exception in execute), PAUSED→RUNNING (when the waiting question receives an accepted answer before timeout), PAUSED→STOPPED (via stop), ERROR→STOPPED (via stop)
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- MUST treat STOPPED as a terminal state — no transition away from STOPPED is allowed

#### Scenario: Waiting question pauses the agent
- GIVEN an agent run that raises a structured question requiring deferred external input
- WHEN the orchestrator enters wait mode for that question
- THEN the agent state MUST transition from `RUNNING` to `PAUSED`

#### Scenario: Accepted answer resumes the paused agent
- GIVEN an agent in `PAUSED` state because one question is waiting for an answer
- WHEN a matching answer is accepted before the wait timeout expires
- THEN the agent state MUST transition from `PAUSED` back to `RUNNING`

#### Scenario: Resume is rejected without a waiting question
- GIVEN an agent session that has no unresolved waiting question
- WHEN a resume attempt is made
- THEN the system MUST reject the attempt
- AND the agent state MUST remain unchanged

### Requirement: DefaultOrchestrator implementation

- MUST be able to suspend the current run when a structured question requiring deferred external input is raised
- While suspended, MUST NOT call `LlmClient.chat` again
- While suspended, MUST NOT execute additional tools
- MUST resume the same conversation after a matching answer is accepted
- MUST stop waiting and end the current run when the configured question wait timeout expires

#### Scenario: Pause prevents additional work
- GIVEN an orchestrator run that has entered question wait mode
- WHEN no answer has been accepted yet
- THEN the system MUST NOT append new assistant turns caused by extra LLM calls
- AND it MUST NOT append new tool results caused by extra tool execution

#### Scenario: Accepted answer resumes the same conversation
- GIVEN an orchestrator run paused on one waiting question
- WHEN a matching answer is accepted before timeout
- THEN the next LLM turn MUST continue from the same session conversation
- AND the accepted answer MUST be present in conversation history before that next turn

#### Scenario: Timeout ends the waiting run
- GIVEN an orchestrator run paused on one waiting question
- WHEN the configured wait timeout expires before any answer is accepted
- THEN the orchestrator MUST end the current wait
- AND it MUST return without executing additional LLM or tool turns for that run

### Requirement: OrchestratorConfig

- MUST be a Java record with `maxTurns` (int, default 50), `toolTimeoutSeconds` (int, default 120), `questionTimeoutSeconds` (int, default 300), and `hooks` (List<ToolExecutionHook>, default empty list)
- MUST provide a static factory `defaults()` returning the default configuration
- MUST provide a static factory `fromMap(Map<String, String>)` constructing config from key-value pairs with fallback to defaults
- MUST provide a convenience constructor without hooks for backward compatibility
- MUST be accepted by DefaultOrchestrator constructor

#### Scenario: Question timeout config comes from map
- GIVEN a config map containing `questionTimeoutSeconds`
- WHEN `OrchestratorConfig.fromMap(Map<String, String>)` is called
- THEN the returned config MUST expose that timeout value
