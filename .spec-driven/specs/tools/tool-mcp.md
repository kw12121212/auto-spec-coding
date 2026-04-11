# Tool MCP Spec

## ADDED Requirements

### Requirement: McpTransport JSON-RPC framing

- MUST send JSON-RPC 2.0 messages over stdin/stdout using Content-Length header framing
- MUST parse incoming Content-Length headers to read complete messages
- MUST use monotonically increasing integer IDs for requests
- MUST correlate requests and responses using CompletableFuture keyed by request ID
- MUST dispatch incoming notifications (messages without ID) to a configurable handler
- MUST return an error if a JSON-RPC response contains an `error` field

### Requirement: McpTransport lifecycle

- MUST launch the external process via ProcessBuilder with the configured command
- MUST start a virtual-thread read loop on construction
- MUST send `shutdown` request and close the process on `close()`
- MUST force-kill the process if it does not exit within 5 seconds after close

### Requirement: McpClient initialize handshake

- MUST send `initialize` request with `protocolVersion`, `clientInfo`, and `capabilities` after transport is ready
- MUST send `notifications/initialized` notification after receiving initialize response
- MUST store the server's reported `capabilities` from the initialize response
- MUST return an error if the server's protocol version is not supported

### Requirement: McpClient tool discovery

- MUST support `tools/list` to discover available tools from the connected MCP server
- MUST return a list of tool descriptors, each containing: name, description, inputSchema
- MUST wrap each discovered tool as a `McpToolAdapter` implementing the `Tool` interface

### Requirement: McpToolAdapter Tool interface compliance

- MUST implement `Tool` interface with `getName()` returning the MCP tool name
- MUST implement `getDescription()` returning the MCP tool description
- MUST implement `getParameters()` converting the MCP `inputSchema` to `List<ToolParameter>`
- MUST implement `execute(ToolInput, ToolContext)` by delegating to `McpClient.callTool()`
- MUST convert MCP tool call results to `ToolResult.Success` (text content) or `ToolResult.Error` (error content)

### Requirement: McpClient tool invocation

- MUST send `tools/call` request with the tool name and arguments
- MUST return the result content from the MCP server response
- MUST return an error if the MCP server reports `isError: true` in the response
- MUST respect the configured timeout per tool call

### Requirement: McpServer initialize handling

- MUST respond to `initialize` requests with `protocolVersion`, `serverInfo`, and `capabilities`
- MUST include `tools` capability in the initialize response

### Requirement: McpServer tools/list handling

- MUST respond to `tools/list` requests with the list of registered tools
- Each tool entry MUST include: name, description, inputSchema (derived from `Tool.getParameters()`)

### Requirement: McpServer tools/call handling

- MUST respond to `tools/call` requests by looking up the named tool in the local tool registry
- MUST call `Tool.execute(ToolInput, ToolContext)` with the provided arguments
- MUST return the tool result as MCP content (text)
- MUST return `isError: true` if the tool returns `ToolResult.Error`

### Requirement: McpServer shutdown handling

- MUST respond to `shutdown` request and exit cleanly

### Requirement: McpClientRegistry management

- MUST manage named MCP client connections
- MUST support `register(name, command)` to create and initialize an MCP client
- MUST support `discoverTools(name)` returning `List<Tool>` for a specific client
- MUST support `discoverAllTools()` returning tools from all registered clients
- MUST support `close()` shutting down all managed clients
- MUST support config-based initialization via `fromConfig(config)` reading `mcp.servers` entries

### Requirement: MCP config format

- Config key `mcp.servers` MUST be a map of server name to server config
- Each server config MUST contain: `command` (string, the shell command to launch the MCP server)
- Each server config MAY contain: `timeout` (integer, per-request timeout in seconds, default 30)
