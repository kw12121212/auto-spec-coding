# Tasks: compile-fallback-isolation

## Implementation

- [x] Add `failedSkillNames()` returning `Set<String>` to the `SkillHotLoader` interface
- [x] Add `ConcurrentHashMap<String, SkillLoadResult> failedRegistry` field to `LealoneSkillHotLoader`
- [x] In `resolveLoader()`, extend the catch block to also catch `SkillCompilationException` and wrap it in `SkillHotLoaderException`
- [x] In `load()`, record result in `failedRegistry` on `success = false`; remove from `failedRegistry` on `success = true`
- [x] In `replace()`, apply the same failed-registry maintenance as `load()`
- [x] In `unload()`, remove the skill name from `failedRegistry`
- [x] Implement `failedSkillNames()` returning `Set.copyOf(failedRegistry.keySet())`

## Testing

- [x] Add test: `compilationExceptionIsWrappedInHotLoaderException` — mock/stub compiler to throw `SkillCompilationException`; assert `load()` throws `SkillHotLoaderException`
- [x] Add test: `failedLoadTrackedInFailedSkillNames` — after `load()` with invalid source returns `success = false`, skill name appears in `failedSkillNames()`
- [x] Add test: `successfulReplaceRemovesFromFailedSkillNames` — after failed `load()`, a successful `replace()` removes the name from `failedSkillNames()`
- [x] Add test: `unloadClearsFailedEntry` — after failed `load()`, `unload()` removes the name from `failedSkillNames()`
- [x] Add test: `failureInSkillADoesNotAffectSkillB` — `load()` skill B successfully, then fail `load()` for skill A; assert skill B still in `loadedSkillNames()` and `activeLoader("skillB")` is non-empty
- [x] Add test: `failedSkillNamesIsUnmodifiable` — assert `UnsupportedOperationException` on mutation attempt
- [x] Run lint/compile: `mvn compile -pl .`
- [x] Run unit tests: `mvn test -pl . -Dtest=SkillHotLoaderTest`

## Verification

- [x] Confirm `SkillCompilationException` no longer escapes through `load()` or `replace()` — only `SkillHotLoaderException` or `SkillLoadResult.success = false` are returned
- [x] Confirm `failedSkillNames()` present in `SkillHotLoader` interface
- [x] Confirm delta spec file `specs/skill/skill-hot-loader.md` reflects the added requirements
