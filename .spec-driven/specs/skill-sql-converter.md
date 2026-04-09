# skill-sql-converter.md

## ADDED Requirements

### Requirement: SkillFrontmatter record

- MUST be a Java record with fields: `skillId` (String), `name` (String), `description` (String), `author` (String), `type` (String), `version` (String)
- `skillId` and `name` MUST be non-null

### Requirement: SkillMarkdownParser

- MUST accept a `Path` to a SKILL.md file
- MUST extract YAML frontmatter delimited by `---` markers
- MUST parse the YAML block into a `SkillFrontmatter` record
- MUST extract all content after the second `---` marker as the instruction body (String, may be empty)
- MUST throw `SkillSqlException` if the file does not contain valid frontmatter with both `---` markers
- MUST throw `SkillSqlException` if required fields `skill_id` or `name` are missing from the frontmatter

### Requirement: SkillSqlConverter SQL generation

- MUST accept a `SkillFrontmatter` and a `Path skillDir`, and return a `String` containing a complete CREATE SERVICE SQL statement
- MUST generate `CREATE SERVICE IF NOT EXISTS` with the `name` field as the service name
- MUST generate a single method `execute(prompt varchar) varchar` in the service body
- MUST include `COMMENT` clause with the `description` value when description is non-null and non-empty
- MUST omit `COMMENT` clause when description is null or empty
- MUST include `LANGUAGE 'skill'` clause
- MUST include `PACKAGE 'org.specdriven.skill'` clause
- MUST include `IMPLEMENT BY` clause with the name converted to PascalCase + `Executor` suffix under the `org.specdriven.skill.executor` package
- MUST include `PARAMETERS` clause with key-value pairs for: `skill_id`, `type`, `version`, `author`, `skill_dir`
- `skill_dir` value MUST be `skillDir.toAbsolutePath().toString()`
- MUST NOT include an inline `instructions` parameter

### Requirement: SkillSqlException

- MUST extend RuntimeException
- MUST accept `(String message)` and `(String message, Throwable cause)` constructors
- MUST include the source file path in the message when the error originates from file parsing

### Requirement: SQL string safety

- MUST escape single quotes in description and parameter values by doubling them (`'` → `''`)
- MUST NOT produce malformed SQL for any valid frontmatter input
