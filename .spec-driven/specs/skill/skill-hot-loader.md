---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoaderException.java
    - src/main/java/org/specdriven/skill/hotload/SkillLoadResult.java
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
    - src/main/java/org/specdriven/skill/hotload/LealoneSkillHotLoader.java
  tests:
    - src/test/java/org/specdriven/skill/hotload/SkillHotLoaderTest.java
---

# skill-hot-loader.md

## Requirements

### Requirement: SkillHotLoaderException

- MUST be a runtime exception in the `org.specdriven.skill.hotload` package
- MUST be used for infrastructure failures in the hot-loader layer (registry state
  errors, ClassLoader construction failures, compiler infrastructure failures)

### Requirement: SkillLoadResult record

- MUST be a Java record in the `org.specdriven.skill.hotload` package
- MUST have fields: `success` (boolean), `entryClassName` (String),
  `diagnostics` (List<SkillCompilationDiagnostic>)
- `diagnostics` MUST be returned as an unmodifiable list

### Requirement: SkillHotLoader interface

- MUST be an interface in the `org.specdriven.skill.hotload` package
- MUST expose whether hot-loading activation is currently enabled
- MUST provide `load(String skillName, String entryClassName, String javaSource, String sourceHash)` returning `SkillLoadResult`
- MUST provide `replace(String skillName, String entryClassName, String javaSource, String sourceHash)` returning `SkillLoadResult`
- MUST provide `unload(String skillName)` returning `void`
- MUST provide `activeLoader(String skillName)` returning `Optional<ClassLoader>`
- MUST provide `loadedSkillNames()` returning `Set<String>`
- MUST provide `failedSkillNames()` returning `Set<String>` — an unmodifiable
  snapshot of skill names whose last compilation or caching attempt returned
  `success = false`

#### Requirement: load

- MUST return `success = false` when hot-loading activation is disabled
- MUST NOT compile source, read or populate the class cache, register an active loader entry, or add the skill name to `failedSkillNames()` when hot-loading activation is disabled
- MUST return `success = false` if `skillName` is already registered
- MUST use the cached `ClassLoader` if `ClassCacheManager.isCached(skillName, sourceHash)` returns `true`
- MUST compile via `SkillSourceCompiler` on cache miss and persist output to `ClassCacheManager.resolveClassDir(skillName, sourceHash)`
- MUST register the active entry and return `success = true` on successful compilation or cache hit
- MUST return `success = false` with forwarded diagnostics on compilation failure and MUST NOT add an entry to the registry

#### Requirement: replace

- MUST return `success = false` when hot-loading activation is disabled
- MUST NOT compile source, read or populate the class cache, modify any existing active loader entry, or add the skill name to `failedSkillNames()` when hot-loading activation is disabled
- MUST follow the same compile-or-cache-hit logic as `load`
- MUST swap the active registry entry only when compilation (or cache lookup) succeeds
- MUST return `success = false` with forwarded diagnostics on compilation failure and MUST NOT modify the existing active entry
- MUST succeed even if `skillName` is not currently registered (behaves as an initial load in that case)

#### Requirement: unload

- MUST remove the active entry for `skillName` from the registry
- MUST be a no-op if `skillName` is not currently registered
- MUST NOT invalidate the class cache

#### Requirement: activeLoader

- MUST return `Optional.of(classLoader)` if `skillName` is currently registered
- MUST return `Optional.empty()` if `skillName` is not registered

#### Requirement: loadedSkillNames

- MUST return an unmodifiable snapshot of the set of currently registered skill names

#### Requirement: failedSkillNames

- MUST return an unmodifiable snapshot of skill names whose last compilation or
  caching `load()`/`replace()` call returned `success = false`
- A skill name MUST be added to the set when `load()` or `replace()` returns
  `SkillLoadResult.success = false` due to a compilation or caching failure
- The duplicate-registration rejection path in `load()` MUST NOT add the skill name
  to the set — the skill is already registered successfully
- A skill name MUST be removed from the set when `load()` or `replace()` returns
  `SkillLoadResult.success = true`
- A skill name MUST be removed from the set when `unload()` is called

### Requirement: LealoneSkillHotLoader

- MUST implement `SkillHotLoader`
- MUST be constructable with a `SkillSourceCompiler` and a `ClassCacheManager`
- MUST start with hot-loading activation disabled by default
- MUST support explicit programmatic enablement by the constructing code path
- MUST use a `ConcurrentHashMap` as the internal active-entry registry
- MUST maintain a second `ConcurrentHashMap` as the internal failed-skill registry
- MUST be in the `org.specdriven.skill.hotload` package
- MUST use independent `ClassLoader` instances for each registered skill so that
  same-named classes in different skills do not conflict
- MUST throw `SkillHotLoaderException` for ClassLoader construction failures
  (wrapping underlying `ClassCacheException`)
- MUST catch `SkillCompilationException` thrown by `SkillSourceCompiler.compile()`
  and wrap it in `SkillHotLoaderException` — a `SkillCompilationException` MUST NOT
  escape through the public `load()` or `replace()` methods

#### Scenario: load succeeds and registers active loader

- GIVEN a valid Java source string for `skillName`
- AND hot-loading activation has been explicitly enabled
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN `SkillLoadResult.success` MUST be `true`
- AND `activeLoader(skillName)` MUST return a non-empty `Optional`
- AND `loadedSkillNames()` MUST contain `skillName`

#### Scenario: default-disabled loader rejects load activation

- GIVEN a newly constructed `LealoneSkillHotLoader`
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called before explicit enablement
- THEN `SkillLoadResult.success` MUST be `false`
- AND `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `loadedSkillNames()` MUST NOT contain `skillName`
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: load rejects duplicate registration

- GIVEN `skillName` is already registered
- WHEN `load(skillName, ...)` is called again
- THEN `SkillLoadResult.success` MUST be `false`
- AND the original active loader MUST remain unchanged

#### Scenario: load with invalid source returns failure without registration

- GIVEN an invalid Java source string
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called
- THEN `SkillLoadResult.success` MUST be `false`
- AND `SkillLoadResult.diagnostics` MUST contain at least one diagnostic
- AND `activeLoader(skillName)` MUST return `Optional.empty()`

#### Scenario: replace with valid source swaps active loader

- GIVEN `skillName` is already registered with version A
- AND hot-loading activation has been explicitly enabled
- WHEN `replace(skillName, entryClassName, newJavaSource, newSourceHash)` is called with valid source
- THEN `SkillLoadResult.success` MUST be `true`
- AND `activeLoader(skillName)` MUST return the new `ClassLoader`

#### Scenario: default-disabled loader rejects replace activation

- GIVEN a newly constructed `LealoneSkillHotLoader`
- WHEN `replace(skillName, entryClassName, javaSource, sourceHash)` is called before explicit enablement
- THEN `SkillLoadResult.success` MUST be `false`
- AND `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: replace with invalid source preserves existing active loader

- GIVEN `skillName` is already registered with a working loader
- WHEN `replace(skillName, entryClassName, invalidSource, newSourceHash)` is called
- THEN `SkillLoadResult.success` MUST be `false`
- AND `activeLoader(skillName)` MUST still return the original loader

#### Scenario: unload removes skill from registry

- GIVEN `skillName` is registered
- WHEN `unload(skillName)` is called
- THEN `activeLoader(skillName)` MUST return `Optional.empty()`
- AND `loadedSkillNames()` MUST NOT contain `skillName`

#### Scenario: unload on absent skill is a no-op

- GIVEN `skillName` is not registered
- WHEN `unload(skillName)` is called
- THEN no exception MUST be thrown

#### Scenario: ClassLoader isolation between skills

- GIVEN two skills loaded with the same entry class name but different source
- WHEN both loaders load the entry class by name
- THEN the two loaded `Class<?>` instances MUST have different identity (different
  `ClassLoader` parents)

#### Scenario: compiler infrastructure failure wraps to SkillHotLoaderException

- GIVEN the compiler throws `SkillCompilationException` when `compile()` is called
- WHEN `load(skillName, ...)` is called
- THEN `SkillHotLoaderException` MUST be thrown
- AND `activeLoader(skillName)` MUST return `Optional.empty()`

#### Scenario: failed load tracked in failedSkillNames

- GIVEN `load(skillName, ...)` with invalid source returns `success = false`
- THEN `failedSkillNames()` MUST contain `skillName`
- AND `loadedSkillNames()` MUST NOT contain `skillName`

#### Scenario: successful replace clears failure record

- GIVEN `skillName` is in `failedSkillNames()` from a prior failed `load()`
- WHEN `replace(skillName, entryClassName, validSource, hash)` returns `success = true`
- THEN `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: unload clears failure record

- GIVEN `skillName` is in `failedSkillNames()` from a prior failed `load()`
- WHEN `unload(skillName)` is called
- THEN `failedSkillNames()` MUST NOT contain `skillName`

#### Scenario: failure in one skill does not affect another

- GIVEN `skillB` is successfully loaded and `activeLoader("skillB")` is non-empty
- WHEN `load("skillA", ...)` fails (invalid source)
- THEN `loadedSkillNames()` MUST still contain `skillB`
- AND `activeLoader("skillB")` MUST still return a non-empty `Optional`

#### Scenario: failedSkillNames is unmodifiable

- WHEN `failedSkillNames()` is called
- THEN mutation attempts on the returned set MUST throw `UnsupportedOperationException`

#### Scenario: duplicate registration does not corrupt failedSkillNames

- GIVEN `skillName` is successfully loaded and present in `loadedSkillNames()`
- WHEN `load(skillName, ...)` is called again (duplicate registration, returns `success = false`)
- THEN `failedSkillNames()` MUST NOT contain `skillName`
- AND `activeLoader(skillName)` MUST still return a non-empty `Optional`

#### Scenario: explicit programmatic enablement restores normal load behavior

- GIVEN a `LealoneSkillHotLoader` whose constructing code path explicitly enabled hot-loading activation
- WHEN `load(skillName, entryClassName, javaSource, sourceHash)` is called with valid source
- THEN `SkillLoadResult.success` MUST be `true`
- AND `activeLoader(skillName)` MUST return a non-empty `Optional`
- AND existing cache and registry semantics MUST remain unchanged
