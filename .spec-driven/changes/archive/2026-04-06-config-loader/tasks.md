# Tasks: config-loader

## Implementation

- [x] Add `snakeyaml` dependency to `pom.xml`
- [x] Create `ConfigException` in `org.specdriven.agent.config`
- [x] Create `Config` class with typed accessors (`getString`, `getInt`, `getBoolean`, `getSection`, `asMap`)
- [x] Create `ConfigLoader` with `load(Path)` and `loadClasspath(String)` factory methods
- [x] Implement dot-notation key resolution for nested YAML maps
- [x] Implement `asMap()` flattening (nested map → `Map<String, String>` with dot-separated keys)
- [x] Implement environment variable substitution (`${VAR_NAME}` pattern)

## Testing

- [x] Unit test: load YAML from filesystem and read top-level keys
- [x] Unit test: load YAML from classpath resource
- [x] Unit test: nested key access via dot notation (`llm.provider`)
- [x] Unit test: `getSection()` returns scoped sub-config
- [x] Unit test: `asMap()` produces correct flattened map
- [x] Unit test: typed accessors with defaults for missing keys
- [x] Unit test: environment variable substitution
- [x] Unit test: `ConfigException` on missing file and malformed YAML
- [x] `mvn compile` passes
- [x] `mvn test` passes

## Verification

- [x] Config files can be loaded from both filesystem and classpath
- [x] Parsed config is accessible as `Map<String, String>` compatible with `Agent.init()`
- [x] Nested YAML structures flatten to dot-notation keys correctly
