# Design: compile-fallback-isolation

## Approach

Two targeted edits to existing files, no new files:

**1. `SkillHotLoader` interface** — add one method:
```java
Set<String> failedSkillNames();
```
Returns an unmodifiable snapshot of skill names whose last `load()` or `replace()`
returned `success = false`.

**2. `LealoneSkillHotLoader`**:

- Add a second `ConcurrentHashMap<String, SkillLoadResult> failedRegistry` alongside
  the existing `registry`.
- In `resolveLoader()`, extend the existing `catch (ClassCacheException e)` block to
  also catch `SkillCompilationException` and wrap it in `SkillHotLoaderException` with
  a descriptive message.
- In `load()`: on `success = false`, record the result in `failedRegistry`; on
  `success = true`, remove from `failedRegistry`.
- In `replace()`: same maintenance as `load()`.
- In `unload()`: remove from `failedRegistry`.
- `failedSkillNames()`: return `Set.copyOf(failedRegistry.keySet())`.

## Key Decisions

**`SkillCompilationException` → `SkillHotLoaderException`, not `success = false`.**
A compilation infrastructure failure (javac unavailable, output directory creation
failed) is fundamentally different from a source error. Callers need to distinguish
"bad source — fix it and retry" from "compiler broken — this is an environment
problem". Wrapping as `SkillHotLoaderException` preserves this distinction, consistent
with how `ClassCacheException` is already handled.

**Failed registry uses `SkillLoadResult` as the value.** This gives callers access to
the diagnostics from the failed attempt without needing a separate query. The registry
is keyed by skill name, so only the most recent failure per skill is retained.

**Failed registry is cleared on unload.** A skill that is explicitly unloaded is no
longer registered and no longer "failed" — it simply does not exist.

## Alternatives Considered

**Convert `SkillCompilationException` to `success = false` with a synthetic
diagnostic.** This would hide the infrastructure failure inside a normal-looking
failure result. Rejected because callers would have no reliable way to distinguish
compiler unavailability from a source error without inspecting diagnostic message text.

**Add `lastFailure(String skillName)` returning `Optional<SkillLoadResult>` instead of
`failedSkillNames()`.** More granular but adds query surface; callers who only need to
know which skills are broken would still need to iterate. Both can coexist, but
`failedSkillNames()` is sufficient for the isolation contract and mirrors the existing
`loadedSkillNames()` pattern.
