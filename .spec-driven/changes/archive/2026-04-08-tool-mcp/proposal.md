# tool-mcp

## What

Implement Model Context Protocol (MCP) adaptation layer: an MCP client that connects to external MCP servers and wraps their tools as M1 `Tool` instances, and an MCP server that exposes agent capabilities as MCP tools to external consumers.

## Why

M10 is the critical dependency bottleneck in the roadmap — it blocks M12 (Native Java SDK), which in turn blocks M13 (JSON-RPC) and M14 (HTTP REST API). Completing MCP integration unblocks the entire interface-layer chain. MCP also enables the agent to dynamically discover and use external tools without hard-coding each one.

## Scope

### In Scope

- MCP protocol types: JSON-RPC 2.0 message framing, initialize/capabilities handshake, tool/resource/prompt schemas
- MCP client: connect to external MCP server via stdio transport, discover tools, wrap each as an M1 `Tool` instance, delegate execution
- MCP server: expose registered `Tool` instances as MCP tools via stdio transport, handle initialize/shutdown lifecycle
- `McpClientRegistry`: manage named MCP client connections, config-based initialization, lifecycle (close all)
- Integration with existing `AgentContext.toolRegistry()`: dynamically inject discovered MCP tools

### Out of Scope

- HTTP SSE transport for MCP (stdio only in this change)
- MCP resource/prompt template features (tools only)
- MCP sampling (LLM delegation to client)
- Authentication/authorization for MCP connections (beyond M6 permissions)
- M12 Native Java SDK facade (separate change)

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing `Tool` interface contract and all current tool implementations (BashTool, GrepTool, LspTool, etc.)
- `AgentContext.toolRegistry()` API signature — MCP tools are injected into the existing Map
- `DefaultOrchestrator` tool dispatch logic — MCP tools are standard `Tool` instances
- `LspClient` — MCP client follows similar JSON-RPC patterns but is independent
