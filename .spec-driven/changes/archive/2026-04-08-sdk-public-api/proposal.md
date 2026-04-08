# sdk-public-api

## What

Create a public SDK facade layer in package `org.specdriven.sdk` that provides a single entry point (`SpecDriven`) with builder pattern, wrapping all core agent capabilities (M1-M10) into a clean, third-party-consumable Java API.

The facade covers:
- Agent lifecycle (create, configure, run, stop)
- Tool registration
- LLM provider auto-assembly from YAML config
- Config loading (plain and vault-aware)
- Permission configuration

## Why

All core infrastructure (M1-M10) is complete, but there is no unified public API for external consumers. The internal types are spread across multiple packages (`org.specdriven.agent.agent`, `org.specdriven.agent.tool`, `org.specdriven.agent.config`, etc.) with no clear public/internal boundary. This change creates the SDK layer that M12 defines as its goal, and provides the stable contract that M13 (JSON-RPC) and M14 (HTTP REST) will build upon.

## Scope

- `org.specdriven.sdk.SpecDriven` — main entry point with builder
- `org.specdriven.sdk.SdkBuilder` — builder for configuring SDK instances
- `org.specdriven.sdk.SdkAgent` — agent handle returned by SDK, exposing run/stop/close
- `org.specdriven.sdk.SdkConfig` — SDK-level configuration record
- `org.specdriven.sdk.SdkException` — unified SDK exception type
- Auto-assembly of LLM provider registry from YAML config
- Vault-aware config loading integration
- Unit tests covering builder, agent creation, and config loading paths

## Unchanged Behavior

- All existing internal interfaces (Agent, Tool, LlmClient, etc.) remain unchanged
- DefaultAgent, DefaultOrchestrator, DefaultLlmProviderRegistry implementations are not modified
- Existing tool implementations (BashTool, ReadTool, etc.) are not modified
- ConfigLoader, VaultFactory behavior unchanged — SDK delegates to them
