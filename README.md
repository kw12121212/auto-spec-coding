# auto-spec-coding

`auto-spec-coding` is a Java reimplementation of `spec-coding-sdk`, built on [Lealone](https://github.com/lealone/Lealone). It exposes the same agent runtime through three release-facing surfaces:

- Native Java SDK
- JSON-RPC over stdin/stdout
- HTTP REST API

The repository already includes the core runtime, built-in tool surface, eventing, registries, permission hooks, cache layers, Service SQL integration, and cross-layer integration tests.

## Maven Coordinates

```xml
<dependency>
  <groupId>org.specdriven.agent</groupId>
  <artifactId>auto-spec-coding</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Prerequisites

- JDK 25+
- Maven 3.3+

## Local Verification

```bash
mvn compile -q
mvn test -q -Dsurefire.useFile=false
```

## Native Java SDK Quickstart

Example config:

```yaml
llm:
  providers:
    openai-main:
      type: openai
      baseUrl: https://api.openai.com/v1
      apiKey: ${OPENAI_API_KEY}
      model: gpt-4.1
  default: openai-main
```

Minimal SDK flow:

```java
import java.nio.file.Path;
import org.specdriven.sdk.SpecDriven;

try (SpecDriven sdk = SpecDriven.builder()
        .config(Path.of("config/agent.yaml"))
        .systemPrompt("You are a concise coding assistant.")
        .build()) {
    String output = sdk.createAgent().run("Explain this repository in one paragraph.");
    System.out.println(output);
}
```

## JSON-RPC Example

The JSON-RPC transport uses `Content-Length` framed messages over stdin/stdout. Start by calling `initialize`, then invoke agent methods such as `agent/run`.

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"systemPrompt":"You are a concise coding assistant."}}
{"jsonrpc":"2.0","id":2,"method":"agent/run","params":{"prompt":"Explain this repository in one paragraph."}}
{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}
```

Supported JSON-RPC methods are defined by the current dispatcher surface, including `initialize`, `shutdown`, `agent/run`, `agent/stop`, `agent/state`, and `tools/list`.

## HTTP REST API Example

`/api/v1/health` is unauthenticated. Other routes accept either `Authorization: Bearer <key>` or `X-API-Key: <key>`.

```bash
curl http://localhost:8080/api/v1/health

curl -X POST http://localhost:8080/api/v1/agent/run \
  -H "Authorization: Bearer test-api-key" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Explain this repository in one paragraph."}'

curl http://localhost:8080/api/v1/tools \
  -H "X-API-Key: test-api-key"
```

## Architecture

- Native Java SDK: direct library integration via `org.specdriven.sdk.SpecDriven`
- JSON-RPC: subprocess-oriented integration over framed stdin/stdout messages
- HTTP REST API: network-accessible agent operations under `/api/v1/*`

## Features

- Tool surface: bash, file ops, grep, glob, LSP client, MCP protocol
- Agent lifecycle with structured events and background-process support
- Task, team, and cron registries backed by Lealone
- Permission model with execution hooks and policy store support
- LLM provider abstraction with OpenAI- and Claude-compatible implementations
- Service SQL integration for skill discovery, instruction loading, and executor plugins
- Spec-driven development workflow under [`.spec-driven/`](.spec-driven/)

## Development Workflow

Specs and roadmap live under:

- [`.spec-driven/specs/INDEX.md`](.spec-driven/specs/INDEX.md)
- [`.spec-driven/roadmap/INDEX.md`](.spec-driven/roadmap/INDEX.md)

Use the shared Java spec-driven CLI to propose, apply, verify, review, and archive changes without Node.js:

```bash
mvn -q -Dexec.mainClass=org.specdriven.cli.SpecDrivenCliMain -Dexec.args="list" exec:java
mvn -q -Dexec.mainClass=org.specdriven.cli.SpecDrivenCliMain -Dexec.args="propose my-change" exec:java
mvn -q -Dexec.mainClass=org.specdriven.cli.SpecDrivenCliMain -Dexec.args="verify my-change" exec:java
```

## License

[Apache License 2.0](LICENSE)
