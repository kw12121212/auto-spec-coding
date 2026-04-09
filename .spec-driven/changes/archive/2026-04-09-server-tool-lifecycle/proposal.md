# server-tool-lifecycle

## What

Add server-class tool readiness probing and resource cleanup to the background process management system. Introduce a `ServerTool` interface extending `BackgroundTool`, a `ReadyProbe` descriptor for declarative probe configuration, and built-in TCP/HTTP probe strategies. Integrate with `ProcessManager` so that server tools are automatically probed for readiness after launch and cleaned up on stop.

## Why

M15's `background-tool-interface` and `process-manager` changes provide generic background process management, but server-class tools (e.g., a database, web server, language server) need an additional readiness step before they can be used. Without readiness probing, the orchestrator has no way to know when a launched server is actually accepting connections. This change closes that gap and completes the M15 milestone's remaining feature work (excluding the agent-integration change).

## Scope

- Define `ServerTool` interface extending `BackgroundTool` with `getReadyProbe()` method
- Define `ReadyProbe` record describing probe type, target, timeout, and retry configuration
- Define `ProbeStrategy` interface with built-in `TcpProbeStrategy` and `HttpProbeStrategy`
- Extend `ProcessManager` to support readiness probing: `waitForReady(String processId, Duration timeout)` returning `boolean`
- Extend `ProcessManager` to support resource cleanup: `cleanup(String processId)` for server-specific teardown
- Emit `SERVER_TOOL_READY` and `SERVER_TOOL_FAILED` event types

## Unchanged Behavior

- Existing `BackgroundTool`, `ProcessManager`, and `DefaultProcessManager` behavior remains unchanged for non-server tools
- `ProcessManager.register()`, `stop()`, `stopAll()`, `getOutput()`, `getState()`, `listActive()` signatures and semantics are preserved
- Agent lifecycle integration (stop-on-agent-stop) is out of scope — that belongs to `background-tool-integ`
