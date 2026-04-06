# config-loader

## What

Implement a YAML-based configuration loader that reads `.yml`/`.yaml` files from the filesystem or classpath and produces structured Java objects. The loader provides both flattened `Map<String, String>` access (compatible with `Agent.init()` and `AgentContext.config()`) and typed section access via a `Config` interface.

## Why

Every subsequent milestone needs configuration: agent initialization (M4), LLM provider credentials (M5), permission rules (M6), HTTP server ports (M14). M1's done criteria explicitly require "配置文件可被正确解析为 Java 对象". Without a config loader, every consumer would reinvent file reading and parsing.

## Scope

- Add `snakeyaml` dependency to `pom.xml`
- New package `org.specdriven.agent.config` with `ConfigLoader`, `Config`, `ConfigSection` types
- Load YAML from filesystem path or classpath resource
- Flatten nested YAML to dot-notation `Map<String, String>` (e.g., `llm.provider.name` → `"openai"`)
- Typed accessor methods: `getString()`, `getInt()`, `getBoolean()`, `getSection()`
- Default values and required-key validation
- Unit tests covering filesystem loading, classpath loading, nested structures, missing keys, defaults

## Unchanged Behavior

- Existing `Agent.init(Map<String, String>)` and `AgentContext.config()` signatures remain unchanged
- `mvn compile` and `mvn test` must continue to pass
- Existing core interfaces (tool, agent, event, permission) are not modified
