# Design: tool-mcp

## Approach

Follow the established patterns from `LspClient` (JSON-RPC over stdio) and `DefaultLlmProviderRegistry` (named instance management):

1. **`McpTransport`** — low-level stdio transport with Content-Length framing and virtual-thread read loop, reused by both client and server roles
2. **`McpClient`** — connects to an external MCP server subprocess, performs `initialize` handshake, discovers tools via `tools/list`, wraps each as an `McpToolAdapter` (implements `Tool`)
3. **`McpServer`** — exposes registered `Tool` instances via stdio, responds to `initialize`, `tools/list`, `tools/call`, `shutdown`
4. **`McpClientRegistry`** — manages named `McpClient` instances, config-driven initialization (server name → command), lifecycle management

Package: `org.specdriven.agent.mcp`

### Class Responsibilities

| Class | Responsibility |
|-------|---------------|
| `McpTransport` | JSON-RPC 2.0 framing over stdin/stdout, message send/receive, CompletableFuture correlation |
| `McpClient` | Manages one MCP server subprocess: initialize, discover tools, call tools |
| `McpServer` | Accepts stdio connection: responds to protocol messages, delegates tool calls to registry |
| `McpToolAdapter` | Implements `Tool`; wraps one discovered MCP tool, delegates `execute()` to `McpClient` |
| `McpClientRegistry` | Named MCP client lifecycle; `discoverTools()` returns `List<Tool>` for injection |

### Data Flow

```
Agent orchestrator
  → calls McpToolAdapter.execute(input, ctx)
    → McpClient.callTool(toolName, arguments)
      → MccpTransport sends JSON-RPC request
        → external MCP server process (stdin/stdout)
      → McpTransport receives JSON-RPC response
    → McpToolAdapter returns ToolResult
```

## Key Decisions

1. **Single transport abstraction** — `McpTransport` handles framing for both client and server roles, avoiding duplication with `LspClient`'s similar but LSP-specific code. MCP uses the same Content-Length framing but different message schemas.

2. **McpToolAdapter implements Tool directly** — each discovered MCP tool becomes a first-class `Tool` instance that the orchestrator dispatches like any other tool. No special MCP dispatch path needed.

3. **Config-driven client initialization** — MCP server connections defined in agent config as `mcp.servers.<name>.command`, loaded by `McpClientRegistry.fromConfig()`. Mirrors `DefaultLlmProviderRegistry.fromConfig()` pattern.

4. **Stdio transport only** — HTTP SSE transport deferred; stdio covers the majority of MCP server integrations and is simpler to implement and test.

5. **Lazy tool discovery** — MCP tools are not discovered until `McpClient.initialize()` is called. The registry exposes `discoverAllTools()` for bulk injection into `AgentContext.toolRegistry()`.

## Alternatives Considered

1. **Reuse LspClient as base class for MCP** — ruled out because LSP has specific semantics (Content-Length framing is shared but message schemas, lifecycle, and error handling differ). Extracting a shared base would be premature; the two protocols may diverge further.

2. **Dynamic proxy for MCP tools** — use `java.lang.reflect.Proxy` to generate Tool instances dynamically. Ruled out in favor of explicit `McpToolAdapter` class for clarity and debuggability.

3. **Centralized McpToolRegistry (separate from AgentContext)** — would introduce a second tool lookup path. Ruled out in favor of injecting MCP tools into the existing `Map<String, Tool>` in `AgentContext.toolRegistry()`.

4. **HTTP SSE transport in this change** — would significantly increase scope. Deferred to a follow-up change when HTTP interface (M14) is in place.
