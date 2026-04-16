# Tasks: platform-config-lifecycle

## Implementation

- [x] Add `PlatformConfig` record to `org.specdriven.sdk` with `jdbcUrl(String)`, `compileCachePath(Path)` fields and a static `PlatformConfig.defaults()` factory matching current hardcoded values
- [x] Add `SdkBuilder.platformConfig(PlatformConfig)` setter
- [x] Update `SdkBuilder.assembleComponents()` to derive effective `PlatformConfig` from: explicit setter > YAML `platform.*` keys > defaults
- [x] Replace all hardcoded `LealonePlatform.DEFAULT_JDBC_URL` and `java.io.tmpdir` derivations in `assembleComponents()` with values sourced from the effective `PlatformConfig`
- [x] Add `AtomicBoolean started` and `AtomicBoolean stopped` fields to `LealonePlatform`
- [x] Add `LealonePlatform.start()`: sets `started` flag idempotently; performs no-op if already started
- [x] Add `LealonePlatform.stop()`: ordered teardown (Interactive → Compiler → LLM → DB) with per-subsystem exception suppression; sets `stopped` flag idempotently
- [x] Update `LealonePlatform.close()` to delegate to `stop()` so teardown logic is not duplicated

## Testing

- [x] Run build validation `mvn compile -pl . -q`
- [x] Run `mvn test -pl . -q` to confirm all existing tests pass
- [x] Add unit test in `LealonePlatformTest`: `start()` completes without error and is safe to call twice
- [x] Add unit test in `LealonePlatformTest`: `stop()` completes without error and is safe to call after `close()`
- [x] Add unit test in `LealonePlatformTest`: `close()` delegates to `stop()` (verify by calling `close()` then `stop()` with no exception)
- [x] Add unit test in `SdkBuilderTest`: `platformConfig(PlatformConfig)` with a custom JDBC URL is reflected in `platform.database().jdbcUrl()`
- [x] Add unit test in `SdkBuilderTest`: omitting `platformConfig(...)` produces the default JDBC URL (backward compat assertion)
- [x] Add unit test in `SdkBuilderTest`: custom `compileCachePath` in `PlatformConfig` is used by the assembled `ClassCacheManager`

## Verification

- [x] Confirm existing `LealonePlatformTest`, `SdkBuilderTest`, and `SpecDrivenTest` pass without modification
- [x] Confirm no caller of `LealonePlatform.close()` is broken — it must remain safe to call directly
- [x] Run `node /home/code/.claude/skills/roadmap-recommend/scripts/spec-driven.js verify platform-config-lifecycle`
