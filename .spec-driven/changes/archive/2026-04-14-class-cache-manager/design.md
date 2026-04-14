# Design: class-cache-manager

## Approach

Introduce a two-type public surface (`ClassCacheException` + `ClassCacheManager`) in `org.specdriven.skill.compiler`, mirroring the package placement of `SkillSourceCompiler`.

**Cache key layout:**
```
<baseDir>/<skillName>/<sourceHash>/
```
`skillName` may contain dots (e.g. `org.example.HelloSkill`); dots are kept as-is in the path segment so the name remains human-readable and avoids collision with package-style subdirectory structures.

**`ClassCacheManager` interface operations:**
- `isCached(skillName, sourceHash)` — checks that the resolved directory exists and contains at least one `.class` file; pure predicate, no side effects
- `resolveClassDir(skillName, sourceHash)` — returns the deterministic path for the cache slot; creates parent directories on first call; does **not** assert that compiled output is present
- `loadCached(skillName, sourceHash)` — asserts `isCached`, then creates and returns a `URLClassLoader` pointing at the resolved directory; throws `ClassCacheException` on cache miss or I/O failure
- `invalidate(skillName, sourceHash)` — deletes the cache slot directory and all contents; no-op if absent

**`LealoneClassCacheManager` constructor:**
Accepts a `Path baseDir`. Callers obtain the base directory from their configuration layer (Lealone `Service.getClassDir()` equivalent or a config-supplied path). This keeps the implementation free of any direct Lealone config coupling at this layer.

**ClassLoader parent chain:**
`URLClassLoader` is constructed with `Thread.currentThread().getContextClassLoader()` as the parent, consistent with how Lealone's `SourceCompiler` loads compiled service classes.

## Key Decisions

1. **Separate interface from hot-loading.** `ClassCacheManager` is strictly cache I/O (resolve / check / load / invalidate). ClassLoader lifecycle management — closing URLClassLoaders on skill unload, preventing leaks — is the responsibility of `SkillHotLoader` (next change). Mixing them would entangle two different resource lifetimes.

2. **`resolveClassDir` creates directories eagerly.** The compiler needs to write into a pre-existing directory. Having `resolveClassDir` guarantee the directory exists means callers can pass its result directly to `SkillSourceCompiler.compile()` without an extra creation step.

3. **`isCached` checks for `.class` presence, not directory existence.** A directory may exist from a previous failed compilation (e.g. the JVM crashed mid-write). Checking for at least one `.class` file is a stronger invariant that a partial write does not produce a false cache hit.

4. **`sourceHash` is caller-computed.** `ClassCacheManager` treats the hash as an opaque string. The contract for computing it (`SHA-256` of the source bytes, hex-encoded, or similar) belongs to the caller. This avoids re-hashing inside the cache layer and lets the hot-loader pass any stable key.

## Alternatives Considered

- **Fold caching into `LealoneSkillSourceCompiler`:** Rejected — it violates single responsibility and makes it impossible to use the cache independently (e.g. for pre-compiled JARs). A separate interface is cleaner and aligns with M30's named component list.

- **Use `sourceHash` as a flat filename prefix rather than a subdirectory:** Rejected — a subdirectory per `(skillName, hash)` slot allows easy per-skill invalidation and mirrors the `Service.getClassDir()` nesting convention used by Lealone.

- **Return `Optional<ClassLoader>` instead of throwing on miss:** Rejected — a missing class directory that was expected is an exceptional condition for `loadCached`; `ClassCacheException` communicates this clearly and forces callers to decide on miss behaviour explicitly.
