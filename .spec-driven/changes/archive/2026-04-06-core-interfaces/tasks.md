# Tasks: core-interfaces

## Implementation

- [x] Create `tool` sub-package (`org.specdriven.agent.tool`)
- [x] Define `ToolParameter` record (name, type, description, required)
- [x] Define `ToolInput` record (parameters Map<String, Object>)
- [x] Define `ToolResult` sealed interface with `Success` and `Error` record implementations
- [x] Define `ToolContext` interface (workDir, env, permissionProvider accessor)
- [x] Define `Tool` interface (getName, getDescription, getParameters, execute)
- [x] Create `agent` sub-package (`org.specdriven.agent.agent`)
- [x] Define `AgentState` enum (IDLE, RUNNING, PAUSED, STOPPED, ERROR)
- [x] Define `AgentContext` interface (sessionId, config, toolRegistry accessor)
- [x] Define `Agent` interface (init, start, stop, close, getState, execute)
- [x] Create `event` sub-package (`org.specdriven.agent.event`)
- [x] Define `EventType` enum (TOOL_EXECUTED, AGENT_STATE_CHANGED, TASK_CREATED, TASK_COMPLETED, CRON_TRIGGERED, ERROR)
- [x] Define `Event` record (type, timestamp, source, metadata)
- [x] Define `EventBus` interface (publish, subscribe, unsubscribe)
- [x] Create `permission` sub-package (`org.specdriven.agent.permission`)
- [x] Define `Permission` record (action, resource, constraints)
- [x] Define `PermissionContext` record (toolName, operation, requester)
- [x] Define `PermissionProvider` interface (check, grant, revoke)
- [x] Add `lealone-orm` dependency to pom.xml for Jackson JSON serialization

## Testing

- [x] Unit test: `ToolResult.Success` and `ToolResult.Error` records are constructed and fields accessed correctly
- [x] Unit test: `ToolParameter` record construction and field access
- [x] Unit test: `Event` record construction with `EventType` values
- [x] Unit test: `Permission` and `PermissionContext` record construction
- [x] Unit test: anonymous `Tool` implementation compiles and `execute` accepts `ToolInput` returning `ToolResult`
- [x] Unit test: `ToolInput` record construction and parameter access
- [x] Unit test: anonymous `EventBus` implementation supports publish/subscribe
- [x] Unit test: anonymous `PermissionProvider` implementation supports check/grant/revoke
- [x] Unit test: `AgentState` enum covers all defined states
- [x] `mvn compile` passes with all new types

## Verification

- [x] All interfaces compile with no warnings
- [x] All unit tests pass
- [x] Package structure matches design layout
- [x] No implementation logic in interfaces — only contracts
