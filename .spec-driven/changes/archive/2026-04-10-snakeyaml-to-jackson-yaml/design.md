# Design: snakeyaml-to-jackson-yaml

## Approach

Replace SnakeYAML with Jackson's `ObjectMapper` backed by `YAMLFactory`. Jackson's `ObjectMapper` is thread-safe after construction, eliminating the concurrency issue that caused flaky tests.

**Shared ObjectMapper instances**: Create one static `ObjectMapper` per class (`ConfigLoader`, `SkillMarkdownParser`). Since `ObjectMapper` is thread-safe after configuration, a shared static instance is both safe and efficient.

**Type mapping**: Jackson's YAML module maps YAML to `Map<String, Object>` via `TypeReference<Map<String, Object>>`. This produces the same nested map structure as SnakeYAML's `Yaml.load()` / `Yaml.loadAs()`.

**Error handling**: Jackson throws `JsonProcessingException` for malformed YAML. Wrap in the existing `ConfigException` / `SkillSqlException` to preserve exception types and message patterns.

## Key Decisions

- **Static shared ObjectMapper** — thread-safe after config, no need for per-call instantiation
- **Keep exception types** — wrap Jackson exceptions inside existing `ConfigException`/`SkillSqlException` so callers see no API change

## Alternatives Considered

- **ThreadLocal<Yaml>** — would fix the concurrency issue but keeps SnakeYAML, which has a worse security track record and is less idiomatic in Jackson-centric projects
- **Upgrade SnakeYAML and synchronize** — adds lock contention without solving the root API mismatch
