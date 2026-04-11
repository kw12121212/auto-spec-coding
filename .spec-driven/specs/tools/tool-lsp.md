# Tool LSP Spec

## ADDED Requirements

### Requirement: LspTool identity

- MUST return `"lsp"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `operation` (string, required), `file` (string, required for all operations), `line` (integer, optional), `character` (integer, optional), `serverCommand` (string, optional — server command, uses default if omitted), `timeout` (integer, optional — per-request timeout in seconds, default 30)

### Requirement: LspTool operation dispatch

- MUST use the `operation` parameter to select the LSP capability to invoke
- MUST support operations: `diagnostics`, `hover`, `goToDefinition`, `references`, `documentSymbols`
- MUST return `ToolResult.Error` if `operation` is not one of the supported values

### Requirement: LspTool diagnostics operation

- MUST open the target document in the language server via `textDocument/didOpen`
- MUST collect diagnostics published via `textDocument/publishDiagnostics` notifications
- MUST return all diagnostic items (severity, message, range) formatted as a human-readable string in `ToolResult.Success`
- MUST return `ToolResult.Error` if no diagnostics arrive within the configured timeout

### Requirement: LspTool hover operation

- MUST require `line` and `character` parameters
- MUST send `textDocument/hover` request to the language server
- MUST return the hover contents formatted as a string in `ToolResult.Success`
- MUST return `ToolResult.Error` if the server returns null/empty hover

### Requirement: LspTool goToDefinition operation

- MUST require `line` and `character` parameters
- MUST send `textDocument/definition` request to the language server
- MUST return the definition locations (file, line, character) formatted as a string in `ToolResult.Success`
- MUST return `ToolResult.Error` if no definitions are found

### Requirement: LspTool references operation

- MUST require `line` and `character` parameters
- MUST send `textDocument/references` request to the language server
- MUST return all reference locations formatted as a string in `ToolResult.Success`
- MUST return `ToolResult.Error` if no references are found

### Requirement: LspTool documentSymbols operation

- MUST send `textDocument/documentSymbol` request to the language server
- MUST return all symbols (name, kind, range) formatted as a string in `ToolResult.Success`
- MUST return `ToolResult.Error` if no symbols are found or the server returns empty

### Requirement: LspClient lifecycle

- MUST launch the language server as a subprocess
- MUST perform LSP `initialize` handshake (send initialize request, receive response, send initialized notification) before any other requests
- MUST send `shutdown` request followed by `exit` notification when closed
- MUST terminate the server process if it does not exit within 5 seconds after `exit` notification

### Requirement: LspClient JSON-RPC transport

- MUST use Content-Length header framing per LSP specification
- MUST correlate requests and responses using monotonically increasing integer IDs
- MUST handle JSON-RPC notifications (no ID) separately from responses
- MUST return `ToolResult.Error` if a response indicates a JSON-RPC error

### Requirement: LspClient error handling

- MUST return `ToolResult.Error` if the language server process fails to start
- MUST return `ToolResult.Error` if the language server process exits unexpectedly
- MUST return `ToolResult.Error` if a request times out waiting for a response
