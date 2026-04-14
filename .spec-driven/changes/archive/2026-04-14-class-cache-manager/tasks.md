# Tasks: class-cache-manager

## Implementation

- [x] Add `ClassCacheException` — runtime exception in `org.specdriven.skill.compiler`
- [x] Add `ClassCacheManager` interface with `isCached`, `resolveClassDir`, `loadCached`, `invalidate` in `org.specdriven.skill.compiler`
- [x] Implement `LealoneClassCacheManager` with `Path baseDir` constructor; cache slot path = `<baseDir>/<skillName>/<sourceHash>/`; use context ClassLoader as URLClassLoader parent

## Testing

- [x] Build: run `mvn compile` — zero compilation errors
- [x] Add `ClassCacheManagerTest` covering: cache miss (`isCached` returns false), cache hit after `.class` files written, `resolveClassDir` creates directory and returns consistent path, `loadCached` returns a ClassLoader that can load the compiled class, `loadCached` throws `ClassCacheException` on miss, `invalidate` removes the cache slot, `invalidate` is a no-op when slot is absent
- [x] Run unit tests: `mvn test -Dtest=ClassCacheManagerTest` — all pass

## Verification

- [x] `ClassCacheManager` interface has exactly four methods matching the spec signatures
- [x] `LealoneClassCacheManager` uses `Thread.currentThread().getContextClassLoader()` as URLClassLoader parent
- [x] `isCached` checks for `.class` file presence, not just directory existence
- [x] `resolveClassDir` creates the directory eagerly
- [x] `invalidate` is a no-op (no exception) when the slot does not exist
- [x] No changes made to `SkillSourceCompiler`, `LealoneSkillSourceCompiler`, or any existing class in the `skill` package tree
