# Design: core-interfaces

## Approach

Define pure Java interfaces and record types organized by concern into sub-packages. Follow Lealone's Plugin interface pattern for lifecycle-aware components (Tool, Agent) and Lealone's async/callback pattern for result handling. Use Java records for immutable value types (ToolParameter, Event, Permission). No implementation classes — only contracts.

### Package Layout

```
org.specdriven.agent/
├── tool/
│   ├── Tool.java              — tool execution contract
│   ├── ToolInput.java         — tool input wrapper (record)
│   ├── ToolResult.java        — execution outcome (sealed interface)
│   ├── ToolContext.java       — execution context (permissions, workdir)
│   └── ToolParameter.java     — parameter descriptor (record)
├── agent/
│   ├── Agent.java             — agent lifecycle contract
│   ├── AgentState.java        — state enum
│   └── AgentContext.java      — agent execution context
├── event/
│   ├── Event.java             — structured event (record)
│   ├── EventBus.java          — pub/sub contract
│   └── EventType.java         — event type enum
└── permission/
    ├── PermissionProvider.java — permission check contract
    ├── Permission.java         — permission descriptor (record)
    └── PermissionContext.java  — check context (record)
```

## Key Decisions

1. **`ToolInput` record wraps tool parameters** — Instead of raw `Map<String, Object>`, a `ToolInput` record carries a parameter map plus extension space for future metadata (trace IDs, timeout hints). Signature stays stable when new fields are added.

2. **`ToolResult` is a sealed interface** — Allows `ToolResult.Success` and `ToolResult.Error` variants. Avoids generic `Result<T>` complexity while keeping the API clean. Consumers pattern-match on the two variants. No need for open extensibility of result types.

3. **`Tool.execute()` returns `ToolResult` synchronously** — Async execution is the caller's responsibility (wrapped in VirtualThread or Lealone Future). The Tool interface itself stays synchronous and composable. M5 LLM backend and M15 background process will handle async at the orchestration layer.

4. **`ToolParameter` uses Java records** — Immutable descriptors for tool parameters. Each has a name, type, description, and required flag. Records provide equals/hashCode/toString for free.

5. **`Agent` follows Lealone Plugin lifecycle** — Methods: `init()`, `start()`, `stop()`, `close()`, `getState()`. Aligns with Lealone's `Plugin` interface pattern so agents can integrate with Lealone's plugin lifecycle management if needed in the future.

6. **`Event` is a record with `EventType` enum** — Structured events carry a type, timestamp, source, and arbitrary metadata map. The `EventType` enum grows as milestones add new event sources (tool execution, agent state changes, cron triggers).

7. **`EventBus` uses `Consumer<Event>` for callbacks** — JDK standard functional interface, keeping the event package framework-agnostic. Event subscriptions in this project are primarily synchronous (state updates, logging). No Lealone dependency in the event package.

8. **`PermissionProvider` is a pure check interface** — Single method `check(Permission, PermissionContext)` returning boolean. No authorization decision logic — that belongs in M6. The interface here only defines the contract for permission queries.

9. **No external annotations on interfaces** — Validation annotations (like `@NonNull`) are not applied at the interface level. Implementations handle validation per their own rules. Keeps interfaces dependency-free.

## Alternatives Considered

- **Generic `Tool<I, O>` with typed input/output** — Rejected. Adds type complexity for minimal gain at this stage. All tools in spec-coding-sdk use string-based or JSON-based parameters. A `ToolInput` record wrapping `Map<String, Object>` + `ToolResult` output keeps things simple and JSON-friendly while allowing future extension.

- **Async `CompletableFuture<ToolResult>` return** — Rejected. JDK 25 VirtualThreads make blocking calls cheap. Async wrapping belongs at the orchestration layer, not in the Tool contract.

- **Single flat package** — Rejected. 16+ types in one package becomes hard to navigate. Sub-packages by concern scale better.

- **Lealone `Plugin` interface inheritance** — Considered but not adopted. Our `Agent` interface mirrors the lifecycle pattern but does not extend `Plugin` directly, avoiding a hard coupling to Lealone internals. The alignment is by convention, not by inheritance.
