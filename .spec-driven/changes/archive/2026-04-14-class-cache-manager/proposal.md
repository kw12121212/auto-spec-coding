# class-cache-manager

## What

Introduce a `ClassCacheManager` interface and its `LealoneClassCacheManager` implementation that persist compiled skill class files to disk, detect cache hits by `skillName + sourceHash` key, and vend `ClassLoader` instances pointing at cached output directories — eliminating redundant recompilation on restart.

## Why

`SkillSourceCompiler` (completed in the previous change) compiles Java skill sources on demand but writes output to a caller-supplied temporary directory with no persistence guarantee. Every restart forces a full recompile of all skills. M30 identified persistent class caching as the next foundational piece before hot-loading and fallback isolation can be built. This change delivers that foundation: a stable, keyed, disk-backed cache that makes compiled skill classes survive across JVM restarts.

## Scope

**In scope:**
- `ClassCacheException` — runtime exception for infrastructure failures in the cache layer
- `ClassCacheManager` interface — `isCached`, `resolveClassDir`, `loadCached`, `invalidate`
- `LealoneClassCacheManager` implementation — organizes class files under `<baseDir>/<skillName>/<sourceHash>/`, creates `URLClassLoader` from cached directories
- Unit tests covering cache-miss, cache-hit, ClassLoader class loading, invalidation, and deeply-nested class names

**Out of scope:**
- ClassLoader lifecycle / explicit close management (covered in `skill-hot-loader`)
- Integration with `SkillAutoDiscovery` or `BuiltinToolManager` (covered in `hot-load-integration`)
- Any LRU eviction, size limits, or TTL policies
- Multi-JVM / distributed cache sharing
- Non-Java skill caching

## Unchanged Behavior

- `SkillSourceCompiler.compile()` contract is unchanged; callers still supply the output directory
- Existing skill discovery, executor, and instruction-store behavior is unaffected
- No changes to `BuiltinToolManager` or any existing tool registration path
