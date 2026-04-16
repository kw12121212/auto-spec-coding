# Tasks: platform-health-metrics

## Implementation

- [x] Add `PLATFORM_HEALTH_CHECKED` and `PLATFORM_METRICS_SNAPSHOT` to `EventType` enum in `src/main/java/org/specdriven/agent/event/EventType.java`
- [x] Create `SubsystemStatus` enum (`UP`, `DEGRADED`, `DOWN`) in `org.specdriven.sdk`
- [x] Create `SubsystemHealth` record (`name`, `status`, `message`) in `org.specdriven.sdk`
- [x] Create `PlatformHealth` record (`overallStatus`, `subsystems`, `probedAt`) in `org.specdriven.sdk` with derived overall status logic
- [x] Create `PlatformMetrics` record (`promptTokens`, `completionTokens`, `compilationOps`, `llmCacheHits`, `llmCacheMisses`, `toolCacheHits`, `toolCacheMisses`, `interactionCount`, `snapshotAt`) in `org.specdriven.sdk`
- [x] Add atomic counter fields to `LealonePlatform` for each `PlatformMetrics` dimension
- [x] In `LealonePlatform.start()`, subscribe to EventBus events (`SKILL_HOT_LOAD_OPERATION`, `INTERACTIVE_COMMAND_HANDLED`) to increment counters; store subscription references for cleanup in `stop()`
- [x] Implement `LealonePlatform.checkHealth()` running DB/LLM/Compiler/Agent probes, publishing `PLATFORM_HEALTH_CHECKED` event, and returning `PlatformHealth`
- [x] Implement `LealonePlatform.metrics()` returning counter snapshot and publishing `PLATFORM_METRICS_SNAPSHOT` event
- [x] In `LealonePlatform.stop()`, unsubscribe metric-accumulation EventBus listeners
- [x] Add `platform()` method returning `Optional<LealonePlatform>` to `SpecDriven`
- [x] Wire `LealonePlatform` into `SpecDriven` when `SdkBuilder.buildPlatform()` is called
- [x] Create `PlatformHealthResponse` record in `org.specdriven.agent.http` for JSON serialization
- [x] Add `GET /platform/health` route to `HttpApiServlet` — delegates to `sdk.platform().checkHealth()` when present; returns 404 otherwise
- [x] Add `encode(PlatformHealthResponse)` to `HttpJsonCodec`

## Testing

- [x] Run `mvn checkstyle:check` (or equivalent lint) to validate code style
- [x] Run `mvn test` to execute the full unit test suite
- [x] Add `PlatformHealthTest` in `src/test/java/org/specdriven/sdk/` — covers `checkHealth()` returning `UP` with a valid platform, probe failure sets `DOWN` for affected subsystem, overall status follows worst-case rule, `PLATFORM_HEALTH_CHECKED` event is published
- [x] Add `PlatformMetricsTest` in `src/test/java/org/specdriven/sdk/` — covers initial counters at zero, counter increments after relevant EventBus events are published, `metrics()` returns snapshot with correct values, `PLATFORM_METRICS_SNAPSHOT` event is published
- [x] Add `SpecDrivenPlatformTest` in `src/test/java/org/specdriven/sdk/` — covers `platform()` returns empty Optional when built without `buildPlatform()`, returns non-empty Optional when built with `buildPlatform()`
- [x] Add `/platform/health` route tests to `HttpApiServletTest` — covers 200 with aggregated JSON when platform is present, 404 when no platform is assembled

## Verification

- [x] Confirm `LealonePlatform.checkHealth()` and `metrics()` are implemented and match proposal scope
- [x] Confirm `GET /api/v1/platform/health` is served and returns subsystem-level JSON
- [x] Confirm existing `GET /api/v1/health` behavior is unchanged
- [x] Confirm `PLATFORM_HEALTH_CHECKED` and `PLATFORM_METRICS_SNAPSHOT` appear in `EventType` and are published correctly
- [x] Confirm `SpecDriven.platform()` returns `Optional.empty()` on the no-platform build path
- [x] Confirm all new tests pass via `mvn test`
