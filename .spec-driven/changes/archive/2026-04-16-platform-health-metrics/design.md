# Design: platform-health-metrics

## Approach

**Health checks** are synchronous point-in-time probes. Each capability domain runs a lightweight probe:
- DB: issue a `SELECT 1` via the configured JDBC URL; `UP` on success, `DOWN` on exception
- LLM: verify at least one provider is registered; `UP` if present, `DEGRADED` if registry is empty
- Compiler: verify `ClassCacheManager` cache path exists and is a writable directory; `UP` on success, `DOWN` on failure
- Agent/Interactive: verify `InteractiveSessionFactory` is non-null; `UP` always (factory is assembled at build time)

`checkHealth()` aggregates the four `SubsystemHealth` entries and derives an overall `SubsystemStatus` (worst-case: if any subsystem is `DOWN`, overall is `DOWN`; if any is `DEGRADED`, overall is `DEGRADED`; otherwise `UP`). After aggregation, a `PLATFORM_HEALTH_CHECKED` event is published to the EventBus with metadata containing the overall status and a probe duration.

**Metrics** are accumulated in-memory using atomic counters. During `start()`, the platform subscribes to EventBus events to increment counters:
- Token usage: `TOOL_EXECUTED` events with LLM-tagged metadata for prompt/completion token counts (already emitted by LLM cache layer)
- Compilation operations: `SKILL_HOT_LOAD_OPERATION` events (already emitted)
- Cache hits/misses: any future cache-tagged events; counters start at zero if no such events are emitted in this change
- Interaction count: `INTERACTIVE_COMMAND_HANDLED` events (already emitted)

`metrics()` returns an immutable snapshot of current counter values plus current timestamp, then publishes a `PLATFORM_METRICS_SNAPSHOT` event with the same values as metadata.

**HTTP surface**: `HttpApiServlet` needs access to `LealonePlatform` to serve `/api/v1/platform/health`. Rather than adding a second constructor parameter, `SpecDriven` gains a `platform()` method returning `Optional<LealonePlatform>`. The servlet calls `sdk.platform()` and routes `/platform/health` to `checkHealth()` when the Optional is non-empty, returning 404 otherwise.

## Key Decisions

1. **Health probes are synchronous, not cached.** Each `checkHealth()` call runs live probes. This keeps the contract simple and avoids stale state. Callers that want polling can call the HTTP endpoint on their own schedule.

2. **Metrics are in-memory counters, not persisted.** The Done Criteria requires queryability, not durability. Persisting metrics would require schema changes and the AuditLogStore already provides event-level durability. In-memory counters are lightweight and restart-safe in the expected single-JVM context.

3. **`SpecDriven.platform()` returns `Optional<LealonePlatform>` rather than requiring platform at build time.** The existing `SpecDriven.builder().build()` path does not require a platform; `buildPlatform()` is a separate call. The Optional preserves backward compatibility with callers that never need platform-level access.

4. **New `/api/v1/platform/health` route, not modifying `/api/v1/health`.** The existing health route is intentionally minimal (no auth required) and returns a static `{"status":"ok"}`. Platform health probes may be slow and contain sensitive subsystem state — they belong on a separate authenticated route.

5. **EventBus metric accumulation uses existing events.** Rather than adding instrumentation calls throughout the codebase, metrics are derived from the already-emitted event stream. This keeps `LealonePlatform` as a thin integration layer per M32's design principle.

## Alternatives Considered

- **Extend existing `/api/v1/health`** to include subsystem details — rejected because it would make a previously unauthenticated, fast endpoint slow and potentially expose internal state to unauthenticated callers.
- **Separate `PlatformMetricsCollector` class** as an injectable component — rejected as over-engineering for a single use site. Counter accumulation belongs directly in `LealonePlatform` during `start()`.
- **Persist metric snapshots to Lealone DB** — rejected for this change; the Done Criteria says "queryable via EventBus or HTTP API", not "durable". A future change can add persistence if needed.
