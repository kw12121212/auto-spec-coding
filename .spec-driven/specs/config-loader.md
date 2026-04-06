# Config Loader Spec

## ADDED Requirements

### Requirement: ConfigLoader entry point

- MUST provide `load(Path)` to load YAML from filesystem
- MUST provide `loadClasspath(String)` to load YAML from classpath
- MUST throw `ConfigException` if the source file does not exist or cannot be read
- MUST throw `ConfigException` if the YAML content is malformed

### Requirement: Config typed access

- MUST provide `getString(String key)` returning `String` or throwing `ConfigException` for missing keys
- MUST provide `getString(String key, String defaultValue)` returning default when key is absent
- MUST provide `getInt(String key, int defaultValue)` parsing string values to int
- MUST provide `getBoolean(String key, boolean defaultValue)` parsing string values to boolean
- MUST support dot-notation keys for nested access (e.g., `"llm.provider"` resolves `{llm: {provider: x}}`)
- MUST be immutable — no setter methods

### Requirement: Config section access

- MUST provide `getSection(String prefix)` returning a `Config` scoped to the nested subtree
- Keys within the sub-config MUST be relative to the section prefix

### Requirement: Config flattening

- MUST provide `asMap()` returning `Map<String, String>` with all nested keys flattened to dot-notation
- The returned map MUST be compatible with `Agent.init(Map<String, String>)`

### Requirement: Environment variable substitution

- SHOULD resolve `${VAR_NAME}` patterns in string values to the corresponding system environment variable
- MUST leave the pattern unresolved (as-is) if the environment variable is not defined
- MUST be opt-in via a parameter on the load method

### Requirement: ConfigException

- MUST be a RuntimeException subclass
- MUST carry a descriptive message indicating the config source and nature of the error
