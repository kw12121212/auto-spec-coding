# Design: skill-instructions-store

## Package

`org.specdriven.skill.store`

## Types

### SkillInstructionStoreException

```java
public class SkillInstructionStoreException extends RuntimeException {
    public SkillInstructionStoreException(String message) { ... }
    public SkillInstructionStoreException(String message, Throwable cause) { ... }
}
```

Thrown when an instruction body or resource file cannot be loaded.

### SkillInstructionStore (interface)

```java
public interface SkillInstructionStore {
    // Level 2: load instruction body from SKILL.md in the given skill directory
    String loadInstructions(String skillId, Path skillDir);

    // Level 3: load a resource file relative to the skill directory
    String loadResource(String skillId, Path skillDir, String relativePath);
}
```

Both methods throw `SkillInstructionStoreException` on I/O or parse failure.

### FileSystemInstructionStore

Stateless implementation that reads directly from the filesystem.

- `loadInstructions`: calls `SkillMarkdownParser.parse(skillDir.resolve("SKILL.md"))` and returns `ParsedSkill.instructionBody()`
- `loadResource`: reads `skillDir.resolve(relativePath)` as a UTF-8 string; throws `SkillInstructionStoreException` if the path resolves outside `skillDir` (path traversal guard) or the file does not exist

## SQL Format Change

`SkillSqlConverter.convert(frontmatter, skillDir)` gains a `Path skillDir` parameter. It:
- Emits `'skill_dir' '<skillDir.toAbsolutePath()>'` in PARAMETERS
- Omits the former inline `'instructions' '...'` parameter

The `convert(SkillFrontmatter, String instructionBody)` overload is removed; callers pass the directory instead.

## SkillAutoDiscovery Change

`discoverAndRegister()` already knows each skill's directory (`Path`). It calls the new `SkillSqlConverter.convert(frontmatter, skillDir)` signature.

## 3-Level Loading Contract

| Level | Content | When | Mechanism |
|-------|---------|------|-----------|
| 1 | `skill_id`, `name`, `description`, `type`, `version`, `author` | Always in SQL PARAMETERS | Lealone reads at service registration |
| 2 | Instruction body (content after `---` in SKILL.md) | On skill trigger | `store.loadInstructions(skillId, skillDir)` |
| 3 | Script resource files in the skill directory | On demand | `store.loadResource(skillId, skillDir, relativePath)` |

The executor retrieves `skill_dir` from PARAMETERS at invocation time, then calls the store.

## Key Decisions

**skill_dir in PARAMETERS, not instructions inline** — avoids SQL length limits for large instruction bodies; the executor always knows the source file path without a separate index.

**Stateless FileSystemInstructionStore** — no construction-time scanning; simpler to test, no stale-index problem if skills/ dir changes after construction.

**Path traversal guard in loadResource** — relativePath is untrusted input; normalize and assert it stays within skillDir before reading.

## Alternatives Considered

**Keep inline `'instructions'` PARAMETER** — simpler converter, but risks hitting parser limits for large skills and defeats the lazy-loading goal.

**Store-internal skillId→Path index** — avoids passing skillDir to every call, but adds mutable state and requires a scan at construction time; the executor already knows skillDir from PARAMETERS.
