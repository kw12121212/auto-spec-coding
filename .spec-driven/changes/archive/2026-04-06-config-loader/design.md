# Design: config-loader

## Approach

Create a `ConfigLoader` that uses SnakeYAML to parse YAML files into a nested `Map<String, Object>`, then provides a `Config` facade over that map with both flattened string access and typed getter methods. The `Config` object is immutable once loaded.

### Package Layout

```
org.specdriven.agent.config/
├── ConfigLoader.java    — entry point: load YAML from file or classpath
├── Config.java          — immutable config facade with typed accessors
└── ConfigException.java — config-specific runtime exception
```

### Data Flow

1. `ConfigLoader.load(Path)` or `ConfigLoader.loadClasspath(String)` reads the YAML source
2. SnakeYAML parses into `Map<String, Object>` (handles nested maps and lists natively)
3. `Config` wraps the parsed map, providing:
   - `get(String key)` → `String` (dot-notation for nested access)
   - `getString(String key, String defaultValue)` → `String`
   - `getInt(String key, int defaultValue)` → `int`
   - `getBoolean(String key, boolean defaultValue)` → `boolean`
   - `getSection(String prefix)` → `Config` (sub-view of nested section)
   - `asMap()` → `Map<String, String>` (flattened dot-notation map for `Agent.init()`)
4. `ConfigException` thrown for file-not-found, parse errors, or missing required keys

### YAML Structure Convention

```yaml
agent:
  name: "my-agent"
  max_retries: 3
llm:
  provider: "openai"
  model: "gpt-4"
  api_key: "${OPENAI_API_KEY}"  # env var substitution
permissions:
  default: "ask"
```

## Key Decisions

1. **SnakeYAML for parsing** — Most mature Java YAML library, handles complex structures, minimal footprint. Added as a compile dependency in `pom.xml`.

2. **Dot-notation flattening** — `llm.provider` maps to the nested `{llm: {provider: "openai"}}`. This produces the `Map<String, String>` that `Agent.init()` and `AgentContext.config()` expect, without changing their signatures.

3. **`Config` is immutable** — Once loaded, the config object cannot be modified. Thread-safe by design. No setter methods.

4. **`getSection()` returns a sub-`Config`** — Instead of a separate `ConfigSection` type, `getSection("llm")` returns a `Config` scoped to that subtree. Reduces type proliferation.

5. **Environment variable substitution** — `${VAR_NAME}` patterns in YAML values are resolved to system env vars at load time. Opt-in via `ConfigLoader.load(path, true)`. This supports API key management without hardcoding secrets.

6. **`ConfigException` extends `RuntimeException`** — Config errors are programming/setup errors, not recoverable conditions. Unchecked exception keeps calling code clean.

7. **Filesystem and classpath loading** — `ConfigLoader.load(Path)` for filesystem, `ConfigLoader.loadClasspath(String)` for classpath resources (e.g., bundled defaults). Both return `Config`.

## Alternatives Considered

- **`java.util.Properties` only** — Rejected per user decision. Properties files are flat and cannot represent the nested structures needed for agent config sections.

- **Jackson YAML module** — Rejected. Jackson's data-binding is overkill for config loading; SnakeYAML is lighter and purpose-built for YAML.

- **HOCON (Typesafe Config)** — Rejected. Introduces a non-standard format. YAML is more widely understood and matches spec-coding-sdk conventions.

- **Mutable `Config` with setters** — Rejected. Config should be loaded once and shared. Mutability introduces thread-safety concerns for no real benefit — reload by creating a new `Config` instance.
