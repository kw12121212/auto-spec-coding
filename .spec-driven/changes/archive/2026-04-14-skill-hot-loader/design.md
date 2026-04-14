# Design: skill-hot-loader

## Approach

`SkillHotLoader` sits directly above `SkillSourceCompiler` and `ClassCacheManager` in
the skill stack. It owns a `ConcurrentHashMap<String, ActiveEntry>` where each
`ActiveEntry` holds the currently live `ClassLoader`, `entryClassName`, and
`sourceHash` for one skill.

**Load flow (`load`):**
1. Reject if `skillName` already has an active entry (return `success = false` with a
   diagnostic message).
2. Check `ClassCacheManager.isCached(skillName, sourceHash)` — if hit, skip compilation.
3. On cache miss, call `SkillSourceCompiler.compile(entryClassName, javaSource, classDir)`
   where `classDir = ClassCacheManager.resolveClassDir(skillName, sourceHash)`.
4. If compilation failed, return a `SkillLoadResult` with `success = false` and
   forwarded diagnostics. Do not add an entry to the registry.
5. On success, call `ClassCacheManager.loadCached(skillName, sourceHash)` to get the
   `ClassLoader`, register the `ActiveEntry`, and return `success = true`.

**Replace flow (`replace`):**
1. Compile (or hit cache) exactly as the load flow above, producing a candidate
   `ClassLoader`.
2. If compilation failed, return `success = false` — the old `ActiveEntry` is unchanged.
3. If compilation succeeded, swap the `ActiveEntry` atomically and return `success =
   true`. The previous `ClassLoader` is replaced; no attempt is made to close it in
   this change (closing is best-effort and requires careful timing across concurrent
   calls).

**Unload flow (`unload`):**
1. Remove the `ActiveEntry` from the registry.
2. No-op if `skillName` was not registered.
3. Does not invalidate the class cache — cached classes remain available for future
   loads.

**`activeLoader(skillName)`:** returns `Optional<ClassLoader>` from the registry.

**`loadedSkillNames()`:** returns an unmodifiable snapshot of the current key set.

## Key Decisions

**1. Cache-first, compiler-second**
Recompilation is skipped when `(skillName, sourceHash)` is already cached. This keeps
hot-reload fast after a restart and matches the caching contract already defined by
`ClassCacheManager`.

**2. Diagnostics forwarded from `SkillCompilationResult`**
`SkillLoadResult` reuses `SkillCompilationDiagnostic` directly rather than introducing
a parallel type. Callers get the same structured error information whether failure
happened at load or replace time.

**3. Duplicate-load guard on `load`; replace is idempotent on success**
`load` is strict (returns failure if already registered) so callers cannot accidentally
shadow an active skill. `replace` is the intended upgrade path.

**4. Safe replace: old loader survives new compilation failure**
The registry is only mutated after a successful compile. This satisfies the M30 done
criterion: "编译失败的新版本 MUST 保持旧版本继续可用".

**5. Package: `org.specdriven.skill.hotload`**
Keeps hot-loader types cleanly separated from the compiler package
(`org.specdriven.skill.compiler`), matching the existing package-per-concern layout.

## Alternatives Considered

**Merge hot-loader into `ClassCacheManager`**
Rejected: cache management and active-registry concerns are distinct. Merging them
would couple ClassLoader lifecycle to disk I/O and break the single-responsibility
pattern already established by the two existing interfaces.

**Close old `ClassLoader` on replace**
Considered for this change but deferred: `URLClassLoader.close()` is I/O-bearing and
requires coordination with threads that may still be executing loaded classes. The safe
close path belongs to `compile-fallback-isolation` or a dedicated resource-management
change, not here.

**Return `Class<?>` instead of `ClassLoader`**
Rejected: returning the raw `ClassLoader` gives callers the flexibility to load any
class from the compiled skill jar, not just the entry class. The integration layer
(`hot-load-integration`) will resolve the concrete class when wiring into
`BuiltinToolManager`.
