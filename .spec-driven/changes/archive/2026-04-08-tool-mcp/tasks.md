# Tasks: tool-mcp

## Implementation

- [x] Create `McpTransport` class: JSON-RPC 2.0 Content-Length framing over stdin/stdout, virtual-thread read loop, CompletableFuture request/response correlation, notification dispatch
- [x] Create `McpClient` class: launch MCP server subprocess via McpTransport, perform `initialize` handshake (send request, validate protocol version, send initialized notification), store server capabilities
- [x] Implement `McpClient.toolsList()`: send `tools/list` request, parse response into tool descriptor list (name, description, inputSchema)
- [x] Implement `McpClient.callTool(name, arguments)`: send `tools/call` request, return result content or error, respect timeout
- [x] Create `McpToolAdapter` class: implement `Tool` interface, derive name/description/parameters from MCP tool descriptor, delegate `execute()` to `McpClient.callTool()`
- [x] Create `McpServer` class: accept stdio connection, handle `initialize`/`tools/list`/`tools/call`/`shutdown` messages, delegate tool calls to local `Map<String, Tool>` registry
- [x] Create `McpClientRegistry` class: manage named `McpClient` instances, `register(name, command)`, `discoverTools(name)`, `discoverAllTools()`, `close()` all clients
- [x] Implement `McpClientRegistry.fromConfig(config)`: parse `mcp.servers` config entries, create and initialize clients
- [x] Add MCP config section to existing config loader test fixtures for validation

## Testing

- [x] Run `mvn compile` to verify no compilation errors
- [x] Write unit tests for `McpTransport`: Content-Length framing encode/decode, request/response correlation, notification dispatch
- [x] Write unit tests for `McpClient`: initialize handshake validation, tool discovery parsing, tool call result conversion
- [x] Write unit tests for `McpToolAdapter`: parameter schema conversion from MCP inputSchema to `List<ToolParameter>`, execute delegation
- [x] Write unit tests for `McpServer`: initialize response generation, tools/list response from local registry, tools/call delegation
- [x] Write unit tests for `McpClientRegistry`: register/discover/close lifecycle, config-based initialization
- [x] Run `mvn test` to verify all tests pass

## Verification

- [x] Verify each spec requirement in `specs/tool-mcp.md` has a corresponding passing test
- [x] Verify `McpToolAdapter` instances work as standard `Tool` instances in the existing orchestrator
- [x] Verify no changes to existing tool implementations or Tool interface
