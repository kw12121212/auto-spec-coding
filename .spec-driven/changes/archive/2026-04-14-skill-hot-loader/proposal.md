# skill-hot-loader

## What

Implement `SkillHotLoader`: the runtime layer that registers, hot-replaces, and unloads
dynamically compiled skill implementations. Given a skill name, entry class name, Java
source, and source hash, `SkillHotLoader` uses the existing `SkillSourceCompiler` and
`ClassCacheManager` to produce a live `ClassLoader` and maintain a per-skill active
loader registry. Replacement is safe-by-default: if a new version fails to compile, the
previously active version remains untouched.

## Why

`SkillSourceCompiler` and `ClassCacheManager` (both already complete in M30) provide the
compile and cache layers, but nothing yet connects them into a live registry of active
skill class loaders. `SkillHotLoader` closes that gap — it is the runtime lifecycle
manager that turns compiled artifacts into registered, replaceable skill instances,
making the M30 goal of "Java source string → executable Skill instance" achievable
end-to-end.

## Scope

**In scope:**
- `SkillHotLoaderException` — runtime exception for loader infrastructure failures
- `SkillLoadResult` record — outcome of a load or replace operation (success flag,
  entry class name, diagnostics forwarded from `SkillCompilationResult`)
- `SkillHotLoader` interface — `load`, `replace`, `unload`, `activeLoader`,
  `loadedSkillNames`
- `LealoneSkillHotLoader` implementation — cache-first strategy using
  `SkillSourceCompiler` + `ClassCacheManager`; per-skill `URLClassLoader`; safe
  hot-replace (old loader survives on new compilation failure)
- Unit tests covering load success, duplicate-load guard, replace success (new loader
  active), replace failure (old loader survives), unload, and ClassLoader isolation

**Out of scope:**
- Compile-failure isolation across concurrent skill loads (covered by
  `compile-fallback-isolation`)
- Integration of `SkillHotLoader` into `SkillAutoDiscovery` or `BuiltinToolManager`
  (covered by `hot-load-integration`)
- Permission gating or audit logging for hot-load operations (covered by M34)
- Closing / garbage-collecting `URLClassLoader` instances (best-effort; no memory-leak
  guarantee in this change)

## Unchanged Behavior

- `SkillSourceCompiler.compile()` and `ClassCacheManager` behaviour and interfaces must
  not change.
- Existing skill discovery via `SkillAutoDiscovery` and execution via
  `SkillServiceExecutor` must continue to work unmodified.
