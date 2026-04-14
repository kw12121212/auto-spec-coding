---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/hotload/SkillHotLoader.java
    - src/main/java/org/specdriven/skill/hotload/LealoneSkillHotLoader.java
  tests:
    - src/test/java/org/specdriven/skill/hotload/SkillHotLoaderTest.java
---

# skill-hot-loader.md — delta for compile-fallback-isolation

## ADDED Requirements

### Requirement: SkillHotLoader.failedSkillNames

- MUST be added to the `SkillHotLoader` interface
- MUST return `Set<String>` — an unmodifiable snapshot of skill names whose last
  compilation or caching attempt returned `success = false`
- A skill name MUST be added to the set when `load()` or `replace()` returns
  `SkillLoadResult.success = false` due to a compilation or caching failure
- The duplicate-registration rejection path in `load()` MUST NOT add the skill name
  to the set — the skill is already registered successfully
- A skill name MUST be removed from the set when `load()` or `replace()` returns
  `SkillLoadResult.success = true`
- A skill name MUST be removed from the set when `unload()` is called

### Requirement: SkillCompilationException isolation in LealoneSkillHotLoader

- `LealoneSkillHotLoader.resolveLoader()` MUST catch `SkillCompilationException`
  thrown by `SkillSourceCompiler.compile()` and wrap it in `SkillHotLoaderException`
- A `SkillCompilationException` MUST NOT escape through the public `load()` or
  `replace()` methods

### Requirement: LealoneSkillHotLoader failed-skill registry

- MUST maintain a `ConcurrentHashMap` failed-skill registry parallel to the active
  entry registry
- `failedSkillNames()` MUST return `Set.copyOf(failedRegistry.keySet())`

## ADDED Scenarios

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
