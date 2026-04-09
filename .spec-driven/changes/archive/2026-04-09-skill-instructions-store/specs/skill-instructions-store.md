# skill-instructions-store

## ADDED Requirements

### Requirement: SkillInstructionStoreException

- MUST extend RuntimeException
- MUST accept `(String message)` and `(String message, Throwable cause)` constructors
- MUST be in the `org.specdriven.skill.store` package

### Requirement: SkillInstructionStore interface

- MUST declare `String loadInstructions(String skillId, Path skillDir)` — loads the instruction body from `skillDir/SKILL.md`
- MUST declare `String loadResource(String skillId, Path skillDir, String relativePath)` — loads a resource file relative to `skillDir`
- Both methods MUST throw `SkillInstructionStoreException` on I/O failure or when the target file does not exist
- MUST be in the `org.specdriven.skill.store` package

### Requirement: FileSystemInstructionStore

- MUST implement `SkillInstructionStore`
- `loadInstructions` MUST parse `skillDir/SKILL.md` using `SkillMarkdownParser` and return the instruction body string
- `loadResource` MUST resolve `relativePath` against `skillDir`, verify the resolved path is within `skillDir` (path traversal guard), and return the file content as a UTF-8 string
- `loadResource` MUST throw `SkillInstructionStoreException` if the resolved path escapes `skillDir`
- `loadResource` MUST throw `SkillInstructionStoreException` if the resolved file does not exist
- MUST be in the `org.specdriven.skill.store` package

## MODIFIED Requirements

### Requirement: SkillSqlConverter SQL generation (modifies skill-sql-converter.md)

- MUST accept `SkillFrontmatter` and `Path skillDir` (replaces the `String instructionBody` parameter)
- MUST include `PARAMETERS` clause with `'skill_dir'` key set to `skillDir.toAbsolutePath().toString()`
- MUST NOT include an inline `'instructions'` parameter
- All other PARAMETERS fields (`skill_id`, `type`, `version`, `author`) MUST remain present and unchanged

### Requirement: SkillAutoDiscovery (modifies skill-auto-discovery.md)

- MUST call `SkillSqlConverter.convert(frontmatter, skillDir)` passing the skill's directory path
- All other `SkillAutoDiscovery` requirements remain unchanged
