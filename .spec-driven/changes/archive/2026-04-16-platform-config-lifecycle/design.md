# Design: platform-config-lifecycle

## Approach

**Step 1 — Introduce `PlatformConfig`**

Add a `PlatformConfig` record to the `org.specdriven.sdk` package with two typed fields:
- `jdbcUrl(String)` — defaults to `"jdbc:lealone:embed:agent_db"` (the current `DEFAULT_JDBC_URL`)
- `compileCachePath(Path)` — defaults to `Path.of(System.getProperty("java.io.tmpdir"), "specdriven-skill-cache")` (the current inline derivation)

Provide a static `PlatformConfig.defaults()` factory so callers that need no customization have a stable reference point.

**Step 2 — Wire `SdkBuilder`**

Add `SdkBuilder.platformConfig(PlatformConfig)` setter. In `assembleComponents()`, replace all hardcoded `LealonePlatform.DEFAULT_JDBC_URL` references and the inline `java.io.tmpdir` derivation with values sourced from the effective `PlatformConfig`. When a YAML config file is loaded, check for `platform.jdbcUrl` and `platform.compileCachePath` keys and use them to override defaults before assembling.

**Step 3 — Add `LealonePlatform.start()` and `stop()`**

- `start()`: records that the platform is running; callable from `SdkBuilder.buildPlatform()` or explicitly by the caller. Safe to call multiple times (idempotent via an `AtomicBoolean started` flag).
- `stop()`: ordered teardown — Interactive → Compiler → LLM → DB (reverse of logical dependency order). Delegates to existing per-capability teardown logic currently in `close()`. Idempotent via an `AtomicBoolean stopped` flag.
- `close()`: delegates to `stop()` to satisfy `AutoCloseable`. Callers using try-with-resources or explicit `close()` are unaffected.

The four capability records (`DatabaseCapability`, `LlmCapability`, `CompilerCapability`, `InteractiveCapability`) are not changed; lifecycle state lives on the `LealonePlatform` instance itself.

## Key Decisions

1. **`PlatformConfig` as a plain record, not a builder** — the two fields are few enough that a record is simpler and avoids a nested builder pattern. If a third field is added later, the record can evolve without breaking callers.

2. **Lifecycle state via `AtomicBoolean`, not a state machine** — the platform has only two meaningful external states (running / stopped). A full state machine would be premature; `AtomicBoolean` is sufficient for idempotency.

3. **`stop()` does not throw on partial failure** — each subsystem teardown is wrapped independently; failures are suppressed (as in the current `close()` impl) so that a single subsystem error does not prevent others from shutting down. This matches the existing behavior of `close()`.

4. **YAML key names `platform.jdbcUrl` / `platform.compileCachePath`** — these are new optional keys; missing keys silently fall back to defaults. No migration is required for existing config files.

5. **`PlatformConfig` in the `org.specdriven.sdk` package** — it is part of the public SDK surface (callers set it via `SdkBuilder`), so it belongs alongside `LealonePlatform` and `SdkBuilder`, not in an internal config package.

## Alternatives Considered

- **Reading config inside `LealonePlatform` directly** — rejected because `LealonePlatform` is an assembled capability holder, not a config reader. Mixing assembly and config concerns would make the platform harder to test with custom configs.
- **Using `Map<String, String>` instead of `PlatformConfig`** — rejected because typed fields provide compile-time safety and are self-documenting; a map requires callers to know magic string keys.
- **Making `start()` block until DB is reachable** — rejected because `LealonePlatform` is a thin glue layer, not a connection manager. Blocking startup belongs to subsystem-specific initialization, not the platform shell. Health checking is deferred to `platform-health-metrics`.
