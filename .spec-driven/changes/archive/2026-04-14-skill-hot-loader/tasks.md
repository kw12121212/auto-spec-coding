# Tasks: skill-hot-loader

## Implementation

- [x] Create `SkillHotLoaderException` runtime exception in package
  `org.specdriven.skill.hotload`
- [x] Create `SkillLoadResult` record in package `org.specdriven.skill.hotload` with
  fields: `success` (boolean), `entryClassName` (String),
  `diagnostics` (List<SkillCompilationDiagnostic>); diagnostics MUST be unmodifiable
- [x] Create `SkillHotLoader` interface in package `org.specdriven.skill.hotload` with
  methods: `load`, `replace`, `unload`, `activeLoader`, `loadedSkillNames`
- [x] Implement `LealoneSkillHotLoader` in package `org.specdriven.skill.hotload`:
  constructor accepts `SkillSourceCompiler` and `ClassCacheManager`; internal registry
  is a `ConcurrentHashMap`
- [x] Implement `load(skillName, entryClassName, javaSource, sourceHash)`: guard against
  duplicate registration, cache-first lookup, compile on miss, register on success
- [x] Implement `replace(skillName, entryClassName, javaSource, sourceHash)`: same
  compile flow as load; only swap registry entry on compilation success; return failure
  result with diagnostics on compilation failure without mutating existing entry
- [x] Implement `unload(skillName)`: remove entry from registry; no-op if absent; do
  not invalidate class cache
- [x] Implement `activeLoader(skillName)`: return `Optional<ClassLoader>` from registry
- [x] Implement `loadedSkillNames()`: return unmodifiable snapshot of current key set

## Testing

- [x] Run lint: `mvn checkstyle:check -pl . 2>&1 | tail -20`
- [x] Run unit tests: `mvn test -pl . -Dtest=SkillHotLoaderTest 2>&1 | tail -40`
- [x] Test: `load` with valid source succeeds and `activeLoader` returns the loader
- [x] Test: `load` with the same skill name twice returns failure on the second call
  and the original loader remains active
- [x] Test: `load` with invalid Java source returns `success = false` with diagnostics;
  skill is not registered
- [x] Test: `replace` with a valid new version swaps the active loader
- [x] Test: `replace` with invalid source returns `success = false` and the original
  loader is still returned by `activeLoader`
- [x] Test: `unload` removes the skill from `loadedSkillNames()` and `activeLoader`
  returns empty
- [x] Test: `unload` on a non-existent skill is a no-op (no exception)
- [x] Test: two skills loaded with the same entry class name use independent
  `ClassLoader` instances (ClassLoader isolation)
- [x] Test: cache-hit path on `load` skips compilation and returns success

## Verification

- [x] Verify all `SkillHotLoader` interface methods are implemented in
  `LealoneSkillHotLoader`
- [x] Verify `replace` never mutates the registry when compilation fails (inspect test
  coverage of the safe-replace scenario)
- [x] Verify `SkillSourceCompiler` and `ClassCacheManager` interfaces are unchanged
- [x] Verify `SkillAutoDiscovery` and `SkillServiceExecutor` are unmodified
