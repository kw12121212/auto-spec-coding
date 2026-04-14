---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/compiler/ClassCacheException.java
    - src/main/java/org/specdriven/skill/compiler/ClassCacheManager.java
    - src/main/java/org/specdriven/skill/compiler/LealoneClassCacheManager.java
  tests:
    - src/test/java/org/specdriven/skill/compiler/ClassCacheManagerTest.java
---

# class-cache-manager.md

## Requirements

### Requirement: ClassCacheException

- MUST be a runtime exception in the `org.specdriven.skill.compiler` package
- MUST be used for infrastructure failures in the cache layer (I/O errors, cache miss on a required entry)

### Requirement: ClassCacheManager

- MUST be an interface in the `org.specdriven.skill.compiler` package
- MUST provide `isCached(String skillName, String sourceHash)` returning `boolean`
- MUST provide `resolveClassDir(String skillName, String sourceHash)` returning `Path`
- MUST provide `loadCached(String skillName, String sourceHash)` returning `ClassLoader`
- MUST provide `invalidate(String skillName, String sourceHash)` returning `void`

#### Requirement: isCached

- MUST return `true` if and only if the resolved directory for the given key exists on disk **and** contains at least one `.class` file
- MUST return `false` for a directory that exists but contains no `.class` files (e.g. from a partial failed write)
- MUST NOT modify the filesystem

#### Requirement: resolveClassDir

- MUST return the deterministic `Path` for cache slot `<baseDir>/<skillName>/<sourceHash>/`
- MUST create the directory hierarchy if it does not already exist
- MUST throw `ClassCacheException` if the directory cannot be created

#### Requirement: loadCached

- MUST return a `ClassLoader` capable of loading classes from the cached directory
- MUST throw `ClassCacheException` if `isCached` returns `false` for the given key
- MUST throw `ClassCacheException` if the `ClassLoader` cannot be constructed due to I/O failure

#### Requirement: invalidate

- MUST delete the cache slot directory and all of its contents for the given key
- MUST be a no-op if no cache entry exists for the given key
- MUST throw `ClassCacheException` if deletion fails

### Requirement: LealoneClassCacheManager

- MUST implement `ClassCacheManager`
- MUST be constructable with a `Path baseDir` argument
- MUST organize cache slots as `<baseDir>/<skillName>/<sourceHash>/`
- MUST use `Thread.currentThread().getContextClassLoader()` as the parent for any `ClassLoader` it creates
- MUST be in the `org.specdriven.skill.compiler` package

#### Scenario: Cache miss returns false from isCached

- GIVEN a `LealoneClassCacheManager` with a writable base directory
- AND no compiled classes have been stored for `(skillName, sourceHash)`
- WHEN `isCached(skillName, sourceHash)` is called
- THEN it MUST return `false`

#### Scenario: Cache hit returns true from isCached after classes are present

- GIVEN compiled `.class` files written to the directory returned by `resolveClassDir(skillName, sourceHash)`
- WHEN `isCached(skillName, sourceHash)` is called
- THEN it MUST return `true`

#### Scenario: resolveClassDir returns consistent path and creates directory

- GIVEN a `LealoneClassCacheManager` with a writable base directory
- WHEN `resolveClassDir(skillName, sourceHash)` is called
- THEN the returned path MUST equal `<baseDir>/<skillName>/<sourceHash>`
- AND the directory MUST exist after the call

#### Scenario: loadCached returns ClassLoader that can load compiled class

- GIVEN compiled `.class` files present in the cache slot for `(skillName, sourceHash)`
- WHEN `loadCached(skillName, sourceHash)` is called
- THEN the returned `ClassLoader` MUST be able to load the compiled entry class by name

#### Scenario: loadCached on cache miss throws ClassCacheException

- GIVEN no `.class` files present for `(skillName, sourceHash)`
- WHEN `loadCached(skillName, sourceHash)` is called
- THEN it MUST throw `ClassCacheException`

#### Scenario: invalidate removes cached classes

- GIVEN compiled `.class` files present in the cache slot for `(skillName, sourceHash)`
- WHEN `invalidate(skillName, sourceHash)` is called
- THEN `isCached(skillName, sourceHash)` MUST return `false`
- AND the cache slot directory MUST no longer exist on disk

#### Scenario: invalidate is a no-op when entry is absent

- GIVEN no cache entry exists for `(skillName, sourceHash)`
- WHEN `invalidate(skillName, sourceHash)` is called
- THEN no exception MUST be thrown
