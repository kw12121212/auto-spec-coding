# Proposal: platform-config-lifecycle

## What

Add ordered `start()` / `stop()` lifecycle methods to `LealonePlatform` and introduce a typed `PlatformConfig` record that consolidates all platform-level parameters (JDBC URL, compile cache path) into a single config object. Wire `SdkBuilder` to populate `PlatformConfig` from loaded YAML config keys (`platform.jdbcUrl`, `platform.compileCachePath`) with fallback to current defaults when keys are absent.

## Why

`LealonePlatform` currently exposes a partial `close()` but has no ordered startup or shutdown. Each subsystem is initialized with hardcoded constants scattered through `SdkBuilder.assembleComponents()` — `LealonePlatform.DEFAULT_JDBC_URL` appears in at least three construction sites, and the compile cache path is derived inline from `java.io.tmpdir`. There is no single place to change a platform parameter, and there is no lifecycle sequence that health checks or migration adapters can observe.

`platform-health-metrics` needs a lifecycle it can hook into to detect readiness of each capability domain. `platform-migration-adapters` needs to know the canonical config source so existing callers can be re-routed without behavioral divergence. Both require this foundation before they can be implemented.

## Scope

**In scope:**
- New `PlatformConfig` record with typed `jdbcUrl` and `compileCachePath` fields; static `defaults()` factory matching current hardcoded values for backward compatibility
- `SdkBuilder.platformConfig(PlatformConfig)` setter; auto-derivation from `platform.*` YAML keys when a config file is loaded
- `LealonePlatform.start()`: idempotent ordered initialization of the four capability domains (DB → LLM → Compiler → Interactive)
- `LealonePlatform.stop()`: idempotent ordered teardown in reverse; `close()` delegates to `stop()`
- `SdkBuilder.assembleComponents()` updated to source all Lealone parameters from `PlatformConfig` instead of inline constants

**Out of scope:**
- Health checks or subsystem readiness probes (covered by `platform-health-metrics`)
- Re-routing existing callers through the platform layer (covered by `platform-migration-adapters`)
- Adding new capability domains beyond the four already defined

## Unchanged Behavior

- All existing capability accessors (`database()`, `llm()`, `compiler()`, `interactive()`) on `LealonePlatform`
- `LealonePlatform.builder()` entry point and `SdkBuilder` public API (`build()`, `buildPlatform()`)
- Callers that do not set `platformConfig(...)` or use `platform.*` YAML keys get exactly the same assembled platform as today
- `close()` continues to implement `AutoCloseable` and is safe to call directly; it delegates to `stop()` after this change
