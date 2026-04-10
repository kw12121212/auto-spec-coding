# snakeyaml-to-jackson-yaml

## What

Replace the SnakeYAML dependency with Jackson-dataformat-yaml (`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`) for all YAML parsing in the project.

## Why

SnakeYAML is not thread-safe — its `Yaml` instances cannot be shared across threads without external synchronization. The previous session exposed this as a source of flaky test failures under JUnit 5 concurrent class execution. Jackson's `ObjectMapper` (with `YAMLFactory`) is thread-safe after configuration, is already a transitive dependency via the Lealone framework, and is the de-facto standard for YAML/JSON handling in Java.

## Scope

- Replace `snakeyaml` dependency in `pom.xml` with `jackson-dataformat-yaml`
- Refactor `ConfigLoader` to use Jackson `ObjectMapper` with `YAMLFactory`
- Refactor `SkillMarkdownParser` to use Jackson `ObjectMapper` with `YAMLFactory`
- Update any tests that directly reference SnakeYAML types

Out of scope:
- Any changes to YAML structure or config schema
- Any changes to observable behavior (error messages, exception types, etc.)

## Unchanged Behavior

- `ConfigLoader.load(Path)`, `loadClasspath(String)` and their vault-aware variants produce identical `Config` instances for the same YAML input
- `ConfigLoader` throws `ConfigException` for missing/malformed files — same message wording
- `SkillMarkdownParser.parse(Path)` produces identical `ParsedSkill` records for the same SKILL.md input
- `SkillMarkdownParser` throws `SkillSqlException` for missing markers or required fields — same message wording
- Environment variable substitution (`${VAR}`) works identically
- All existing tests pass without behavioral changes
