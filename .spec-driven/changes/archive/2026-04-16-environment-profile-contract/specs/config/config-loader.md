---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/config/Config.java
    - src/main/java/org/specdriven/agent/config/ConfigLoader.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/config/ConfigLoaderTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
---

## MODIFIED Requirements

### Requirement: ConfigLoader entry point
Previously: 
- MUST provide `load(Path)` to load YAML from filesystem
- MUST provide `loadClasspath(String)` to load YAML from classpath
- MUST throw `ConfigException` if the source file does not exist or cannot be read
- MUST throw `ConfigException` if the YAML content is malformed

The system MUST continue to provide `load(Path)` and `loadClasspath(String)` for YAML loading, and project YAML loading MUST additionally support the environment-profile declaration contract defined by this change.

#### Scenario: project YAML profile section loads through existing config path
- GIVEN a readable project YAML file that declares a supported environment-profile section
- WHEN `ConfigLoader.load(Path)` or `ConfigLoader.loadClasspath(String)` loads that file
- THEN loading MUST preserve the declared profile configuration for later supported selection
- AND loading MUST continue to expose the rest of the YAML configuration through the existing config facade

#### Scenario: malformed profile declaration fails as config error
- GIVEN a readable project YAML file whose environment-profile section is structurally invalid for the supported profile contract
- WHEN `ConfigLoader.load(Path)` or `ConfigLoader.loadClasspath(String)` loads that file through a supported repository configuration path
- THEN loading or the immediately dependent config assembly path MUST fail explicitly
- AND the failure MUST be surfaced as a configuration error

### Requirement: Config typed access
Previously: 
- MUST provide `getString(String key)` returning `String` or throwing `ConfigException` for missing keys
- MUST provide `getString(String key, String defaultValue)` returning default when key is absent
- MUST provide `getInt(String key, int defaultValue)` parsing string values to int
- MUST provide `getBoolean(String key, boolean defaultValue)` parsing string values to boolean
- MUST support dot-notation keys for nested access (e.g., `"llm.provider"` resolves `{llm: {provider: x}}`)
- MUST be immutable — no setter methods

The config typed access contract MUST continue to support dot-notation access for nested YAML data, including project-level environment-profile declarations introduced by this change.

#### Scenario: nested environment profile keys remain addressable
- GIVEN a loaded project YAML config that declares a named environment profile under the supported profile section
- WHEN a supported repository configuration path reads nested profile values through the config facade
- THEN nested profile values MUST remain addressable through the repository's supported nested config access model
- AND this change MUST NOT require callers to bypass the config facade to inspect declared profile data

### Requirement: Config flattening
Previously: 
- MUST provide `asMap()` returning `Map<String, String>` with all nested keys flattened to dot-notation
- The returned map MUST be compatible with `Agent.init(Map<String, String>)`

The config flattening contract MUST continue to flatten nested YAML keys, including declared environment-profile configuration keys, into the repository's dot-notation map representation.

#### Scenario: environment profile keys participate in flattening
- GIVEN a loaded project YAML config that declares a supported environment-profile section
- WHEN `asMap()` is called on the loaded config
- THEN the flattened output MUST include the declared profile keys using the same dot-notation strategy as other nested configuration keys
- AND existing non-profile flattening behavior MUST remain unchanged
