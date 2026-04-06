# core-interfaces

## What

Define the core Java interfaces that form the type contract for the entire agent system: `Tool`, `ToolResult`, `ToolContext`, `Agent`, `AgentState`, `AgentContext`, `Event`, `EventBus`, and `PermissionProvider`. These interfaces reside under `org.specdriven.agent` with sub-packages for each concern, and are designed to be implemented by concrete modules in M2–M16.

## Why

Every subsequent milestone depends on these interfaces. M2 tools implement `Tool`, M4 agents implement `Agent`, M6 permissions implement `PermissionProvider`, and M7–M8 registries emit `Event`s. Defining them early ensures a consistent contract across all layers (SDK, JSON-RPC, HTTP) and avoids interface refactoring churn later.

## Scope

- `org.specdriven.agent.tool` — `Tool`, `ToolResult`, `ToolContext`, `ToolParameter`
- `org.specdriven.agent.agent` — `Agent`, `AgentState`, `AgentContext`
- `org.specdriven.agent.event` — `Event`, `EventBus`, `EventType`
- `org.specdriven.agent.permission` — `PermissionProvider`, `Permission`, `PermissionContext`
- Record-based value types for parameters and results where appropriate
- Unit tests verifying interface contracts (instantiation via anonymous impls, serialization shape)

## Unchanged Behavior

No runtime behavior exists yet. The `project-scaffold` Maven build and directory structure must remain unchanged — `mvn compile` and `mvn test` must still pass after this change.
