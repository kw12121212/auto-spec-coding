# hot-load-integration

## What

Connect the `SkillHotLoader` pipeline to `SkillAutoDiscovery` so that skills with an
accompanying Java source file are compiled and registered in the hot-loader registry
during discovery. Also connect `SkillHotLoader` to `SkillServiceExecutorFactory` so
that executor classes loaded via the hot-loader take precedence over the system
classloader when the factory instantiates a skill's executor.

## Why

M30 built the compilation, caching, and hot-load registry in isolation. Without this
integration change, the dynamically compiled classes sit in the registry but are never
used — `SkillAutoDiscovery` still only registers services via SQL DDL, and
`SkillServiceExecutorFactory` still loads executor classes from the system classloader.
This change closes the loop: skills discovered from a directory tree that include Java
source get compiled on discovery, and the factory resolves those classes from the
hot-loader ClassLoader at invocation time.

## Scope

### In scope

- Extend `SkillAutoDiscovery` constructor to accept an optional `SkillHotLoader`
- During `discoverAndRegister()`, for each skill directory that contains a Java source
  file alongside `SKILL.md`, read the source and invoke `hotLoader.load()`
- Extend `DiscoveryResult` with `hotLoadedCount` and `hotLoadFailedCount` to surface
  hot-load outcomes alongside the existing SQL registration counts; hot-load errors are
  appended to the existing `errors` list using the same `SkillDiscoveryError` type
- Extend `SkillServiceExecutorFactory` to hold an optional `SkillHotLoader` reference;
  when the factory creates an executor for a skill whose name is registered in the
  hot-loader, instantiate the executor class from the hot-loaded `ClassLoader` instead
  of the system classloader
- Unit tests for both integration points

### Out of scope

- Changing the SQL DDL registration path in `SkillAutoDiscovery`
- Changing the `SkillHotLoader` interface
- Changing `BuiltinToolManager` (binary tool management for rg/fd)
- Exposing hot-loaded skills as `Tool` interface implementations
- Multi-version or concurrent-replacement behavior during a live discovery run
- Security governance for dynamic compilation (covered by M34)

## Unchanged Behavior

- `SkillAutoDiscovery` constructed without a `SkillHotLoader` MUST behave identically
  to the current implementation — no SQL registration path changes
- Skills without an accompanying Java source file are NOT hot-loaded; their SQL
  registration proceeds as before
- Existing `DiscoveryResult` fields `registeredCount` and `failedCount` retain their
  current SQL registration semantics; `errors` becomes a shared per-skill error list
  that may include either SQL registration failures or hot-load failures
- `BuiltinToolManager` binary tool resolution is not touched
