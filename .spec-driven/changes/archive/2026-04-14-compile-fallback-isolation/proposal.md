# compile-fallback-isolation

## What

Add compilation failure isolation to `LealoneSkillHotLoader`:

1. Catch `SkillCompilationException` (infrastructure-level compiler failure) inside
   `resolveLoader()` and wrap it in `SkillHotLoaderException` — consistent with how
   `ClassCacheException` is already handled — so no infrastructure exception leaks
   through the public `load()` / `replace()` API.

2. Track skills in a failed state by adding `failedSkillNames()` to the `SkillHotLoader`
   interface and maintaining a concurrent failed-skill registry in
   `LealoneSkillHotLoader`. A skill name enters the registry when its last `load()` or
   `replace()` call returned `success = false`; it is cleared on a subsequent successful
   call or `unload()`.

## Why

`LealoneSkillHotLoader.resolveLoader()` currently catches `ClassCacheException` but
not `SkillCompilationException`. A javac infrastructure failure (compiler unavailable,
output directory unwritable) therefore escapes the hot-loader API as an unchecked
`SkillCompilationException`, violating the layer boundary and exposing callers to an
exception type from a lower layer.

There is also no observable way to tell which skills are in a failed state — callers
must track this themselves. A built-in `failedSkillNames()` query closes this gap and
makes the failure boundary explicit.

## Scope

In scope:
- `SkillHotLoader` — add `failedSkillNames()` method to the interface
- `LealoneSkillHotLoader` — catch `SkillCompilationException` in `resolveLoader()`;
  add a `ConcurrentHashMap` failed-skill registry; maintain it in `load()`,
  `replace()`, and `unload()`; implement `failedSkillNames()`
- `SkillHotLoaderTest` — add tests for infrastructure exception wrapping, failed-skill
  tracking, failure clearing on success, cross-skill isolation, and
  `failedSkillNames()` unmodifiability

Out of scope:
- Modifying `SkillSourceCompiler`, `ClassCacheManager`, or any compiler/cache type
- Changing what `SkillCompilationResult.success = false` means (source error path)
- Retry logic or automatic recompilation on failure

## Unchanged Behavior

- All existing `SkillHotLoader` contract (load, replace, unload, activeLoader,
  loadedSkillNames) is unchanged
- `SkillCompilationResult.success = false` (source syntax error) continues to return
  `SkillLoadResult.success = false` — only the thrown-exception path changes
- Compilation failure in one skill does not remove or modify the active loader of any
  other skill
