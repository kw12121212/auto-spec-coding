---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/compiler/SkillCompilationDiagnostic.java
    - src/main/java/org/specdriven/skill/compiler/SkillCompilationException.java
    - src/main/java/org/specdriven/skill/compiler/SkillCompilationResult.java
    - src/main/java/org/specdriven/skill/compiler/SkillSourceCompiler.java
    - src/main/java/org/specdriven/skill/compiler/LealoneSkillSourceCompiler.java
  tests:
    - src/test/java/org/specdriven/skill/compiler/SkillSourceCompilerTest.java
---

# skill-source-compiler.md

## ADDED Requirements

### Requirement: SkillCompilationDiagnostic record

- MUST be a Java record with fields: `message` (String), `lineNumber` (long), and `columnNumber` (long)
- MUST be in the `org.specdriven.skill.compiler` package

### Requirement: SkillCompilationResult record

- MUST be a Java record with fields: `success` (boolean), `entryClassName` (String), and `diagnostics` (List<SkillCompilationDiagnostic>)
- `diagnostics` MUST be returned as an unmodifiable list
- MUST be in the `org.specdriven.skill.compiler` package

### Requirement: SkillCompilationException

- MUST be a runtime exception in the `org.specdriven.skill.compiler` package
- MUST be used for infrastructure failures that prevent the compiler from producing a normal `SkillCompilationResult`

### Requirement: SkillSourceCompiler

- MUST provide `compile(String entryClassName, String javaSource, Path outputDir)` returning `SkillCompilationResult`
- MUST compile the provided Java source into the caller-supplied output directory
- MUST create the output directory if it does not already exist
- MUST return `success = true` and `entryClassName` equal to the requested entry class when compilation succeeds
- MUST return `success = false` and one or more diagnostics when the source cannot be compiled
- MUST NOT register, load, or activate the compiled skill as part of compilation
- MUST throw `SkillCompilationException` when runtime compiler capability is unavailable or the output directory cannot be prepared
- MUST be in the `org.specdriven.skill.compiler` package

#### Scenario: Successful compilation writes class output

- GIVEN a valid Java skill source string, an entry class name, and a writable output directory
- WHEN `compile(entryClassName, javaSource, outputDir)` is called
- THEN the returned `SkillCompilationResult.success` MUST be `true`
- AND the returned `entryClassName` MUST equal the requested entry class name
- AND the output directory MUST contain readable `.class` output for the requested entry class

#### Scenario: Invalid source returns diagnostics

- GIVEN an invalid Java skill source string and a writable output directory
- WHEN `compile(entryClassName, javaSource, outputDir)` is called
- THEN the returned `SkillCompilationResult.success` MUST be `false`
- AND `diagnostics` MUST contain at least one compiler message describing the source error

### Requirement: Lealone-backed skill source compiler

- The system MUST provide a `LealoneSkillSourceCompiler` implementation of `SkillSourceCompiler`
- It MUST compile directly from a Java source string without requiring the caller to persist a repository-managed `.java` source file first
- It MUST use the Lealone-backed compiler capability available from the current runtime dependency set
- It MUST preserve compiler diagnostics for invalid source in `SkillCompilationResult.diagnostics`

#### Scenario: Missing compiler capability fails fast

- GIVEN the current runtime dependency set does not expose the required compiler capability
- WHEN a `LealoneSkillSourceCompiler` attempts compilation
- THEN it MUST throw `SkillCompilationException`
- AND the exception message MUST describe that the required compiler capability is unavailable
