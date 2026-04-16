# platform-health-metrics

## What

Add aggregated health-check probes and metric counters to `LealonePlatform`, and expose both via EventBus events and a new `/api/v1/platform/health` HTTP endpoint.

New observable surface:
- `LealonePlatform.checkHealth()` — probes DB, LLM, Compiler, and Agent subsystems and returns an aggregated `PlatformHealth` result
- `LealonePlatform.metrics()` — returns a `PlatformMetrics` snapshot of cumulative counters (token usage, compilation operations, cache hits/misses, interaction count) accumulated since `start()`
- `GET /api/v1/platform/health` — new HTTP route that delegates to `checkHealth()` and returns JSON
- `PLATFORM_HEALTH_CHECKED` and `PLATFORM_METRICS_SNAPSHOT` additions to `EventType`, published when health or metrics are queried
- `SpecDriven.platform()` — returns the optionally assembled `LealonePlatform` when one was built via `SdkBuilder.buildPlatform()`

## Why

M32 Done Criteria require:
- "平台健康检查 MUST 聚合 DB 连接池、LLM endpoint、编译器、Agent 交互各子系统状态"
- "平台度量指标 MUST 可通过 EventBus 或 HTTP API 查询"

This is the third of four M32 planned changes. Completing it satisfies the health and metrics Done Criteria and is a prerequisite for `platform-migration-adapters`, which closes M32 and unblocks M36.

## Scope

In scope:
- `PlatformHealth` record — overall status, per-subsystem `SubsystemHealth` list, probe timestamp
- `SubsystemHealth` record — subsystem name, `SubsystemStatus` enum (`UP`, `DEGRADED`, `DOWN`), optional detail message
- `PlatformMetrics` record — prompt tokens used, completion tokens used, compilation operations count, LLM cache hits/misses, tool cache hits/misses, interaction count, snapshot timestamp
- `LealonePlatform.checkHealth()` — synchronous probes of DB/LLM/Compiler/Agent; publishes `PLATFORM_HEALTH_CHECKED` event; returns `PlatformHealth`
- `LealonePlatform.metrics()` — returns current counter snapshot; publishes `PLATFORM_METRICS_SNAPSHOT` event
- On `start()`, platform subscribes to EventBus to accumulate metric counters from existing event stream
- `SpecDriven.platform()` accessor — returns `Optional<LealonePlatform>`; non-empty when `buildPlatform()` was called
- `GET /api/v1/platform/health` HTTP route — delegates to `platform().checkHealth()` when available; returns 404 otherwise
- `PlatformHealthResponse` HTTP response model for JSON serialization
- Two new `EventType` values: `PLATFORM_HEALTH_CHECKED`, `PLATFORM_METRICS_SNAPSHOT`

Out of scope:
- Replacing or modifying the existing `/api/v1/health` route
- Metric persistence to database (counters are in-memory, resetted on restart)
- Alert thresholds or automatic remediation based on health status
- Push-based metric streaming (collection is pull-based via `metrics()`)

## Unchanged Behavior

- Existing `GET /api/v1/health` route behavior and `HealthResponse` format
- All `LealonePlatform` capability accessors (`database()`, `llm()`, `compiler()`, `interactive()`)
- Existing `start()`, `stop()`, `close()` lifecycle semantics
- All existing `SpecDriven`, `SdkBuilder`, `SdkAgent` behavior and API
- All existing `EventType` values, EventBus pub/sub semantics, and AuditLogStore behavior
- All existing HTTP routes, authentication, and rate-limit behavior
