# Tasks: snakeyaml-to-jackson-yaml

## Implementation

- [x] Replace `snakeyaml` dependency with `jackson-dataformat-yaml` in `pom.xml`
- [x] Refactor `ConfigLoader` to use Jackson `ObjectMapper` with `YAMLFactory` instead of `new Yaml().load()`
- [x] Refactor `SkillMarkdownParser` to use Jackson `ObjectMapper` with `YAMLFactory` instead of `new Yaml().loadAs()`

## Testing

- [x] Run `mvn compile` lint and compilation validation
- [x] Run `mvn test` — unit tests for ConfigLoader, SkillMarkdownParser, and full suite pass

## Verification

- [x] Verify no `org.yaml.snakeyaml` imports remain in source code
- [x] Verify `ConfigLoader` and `SkillMarkdownParser` produce identical results for existing test inputs
