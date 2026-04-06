# auto-spec-coding

Java SDK reimplementation of [spec-coding-sdk](https://github.com/anthropics/claude-code), exposing coding agent capabilities through multiple interface layers, built on [Lealone](https://github.com/lealone/Lealone).

## Architecture

The SDK provides three interface layers for third-party integration:

- **Native Java SDK** — direct library usage via Maven dependency
- **JSON-RPC** over stdin — subprocess integration
- **HTTP REST API** — service-oriented integration (powered by Lealone HTTP server)

## Features

- Tool surface: bash, file ops, grep, glob, LSP client, MCP protocol
- Agent lifecycle with structured event system (VirtualThread-based)
- Task, team, and cron registries (Lealone DB persisted)
- Permission model with execution hooks
- LLM backend integration (OpenAI, Anthropic Claude, extensible)
- Service SQL integration — define agent tools via `CREATE SERVICE` SQL (Lealone native)
- Built-in spec-driven development workflow

## Tech Stack

- Java 25+ (VirtualThread)
- [Lealone](https://github.com/lealone/Lealone) — embedded DB, HTTP server, scheduling, async networking
- Maven
- Apache 2.0 Licensed

## Development

This project uses a [spec-driven](/.spec-driven/) development workflow. See [`.spec-driven/roadmap/INDEX.md`](/.spec-driven/roadmap/INDEX.md) for the milestone plan.

### Prerequisites

- JDK 25+
- Maven 3.3+

### Build & Test

```bash
mvn compile
mvn test
```

## Project Status

Early development — roadmap defined, implementation not yet started. See the [roadmap](/.spec-driven/roadmap/INDEX.md) for 16 planned milestones.

## License

[Apache License 2.0](LICENSE)
