# Agent Interface Delta Spec

## ADDED Requirements

### Requirement: AgentContext.processManager()

- MUST add method `processManager()` to `AgentContext` interface
- MUST return `Optional<ProcessManager>` — empty if no process manager is available
- MUST have a default implementation returning `Optional.empty()` for backward compatibility

### Requirement: SimpleAgentContext ProcessManager support

- MUST add `ProcessManager` field to `SimpleAgentContext`
- MUST add constructor overload accepting `ProcessManager` as additional parameter
- MUST retain existing constructor for backward compatibility (passing null ProcessManager)
- MUST implement `processManager()` returning `Optional.ofNullable(processManager)`

### Requirement: DefaultAgent background process cleanup on stop

- MUST call `cleanupBackgroundProcesses()` at the end of `stop()` method
- MUST perform cleanup AFTER state transition to STOPPED
- MUST NOT allow cleanup failures to prevent state transition

### Requirement: DefaultAgent background process cleanup on close

- MUST call `cleanupBackgroundProcesses()` at the end of `close()` method
- MUST act as a safety net for direct close() calls without prior stop()

### Requirement: DefaultAgent.cleanupBackgroundProcesses() helper

- MUST be a private method in `DefaultAgent`
- MUST check if the last `AgentContext` used in `doExecute()` has a `ProcessManager`
- MUST call `processManager.stopAll()` if ProcessManager is present
- MUST catch and swallow any exceptions from `stopAll()` (cleanup best-effort)
- MUST store reference to `AgentContext` during `doExecute()` for later cleanup access

## CHANGED Requirements

### Requirement: DefaultAgent.doExecute context storage

- MUST store the provided `AgentContext` in a field for access during cleanup
- MUST clear the stored context after execution completes (in finally block)
