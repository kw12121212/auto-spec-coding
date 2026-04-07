# tool-lsp — Design

## Approach

Introduce two new classes:

1. **`LspClient`** — manages a single language server process. Responsible for:
   - Launching the server process with configurable command
   - Sending/receiving JSON-RPC 2.0 messages over stdin/stdout
   - LSP initialize/initialized/shutdown/exit handshake
   - Routing responses and notifications to callers
   - Tracking request IDs for request/response correlation

2. **`LspTool`** — implements `Tool` interface. Delegates to an `LspClient` instance. Accepts an `operation` parameter to select which LSP capability to invoke.

### JSON-RPC layer

A lightweight standalone JSON-RPC 2.0 implementation lives inside the `LspClient`:
- Content-Length header framing (LSP spec)
- Request/response correlation via integer IDs
- Notification handling (critical for diagnostics)

This is intentionally self-contained. M13 may later extract a shared JSON-RPC codec; the current layer keeps loose coupling by not exposing its JSON-RPC types outside `LspClient`.

### LSP operation mapping

| Tool `operation` param | LSP method | Input params |
|---|---|---|
| `diagnostics` | textDocument/publishDiagnostics (notification) | `file` |
| `hover` | textDocument/hover | `file`, `line`, `character` |
| `goToDefinition` | textDocument/definition | `file`, `line`, `character` |
| `references` | textDocument/references | `file`, `line`, `character` |
| `documentSymbols` | textDocument/documentSymbol | `file` |

For diagnostics, the client opens the document via `textDocument/didOpen`, then collects published diagnostics. A `diagnostics` operation triggers a didOpen + waits for the diagnostic notification.

### Connection lifecycle

- `LspClient` is created with a server command (e.g. `["jdtls", "-data", "/tmp/jdtls-workspace"]`)
- `initialize` + `initialized` on first use
- `textDocument/didOpen` / `textDocument/didClose` managed per-operation
- `shutdown` + `exit` on `LspClient.close()` or tool disposal

## Key Decisions

1. **Single connection per LspTool instance** — each `LspTool` holds one `LspClient` targeting one language server. Multi-server use means multiple tool instances. This keeps lifecycle management simple.

2. **Standalone JSON-RPC** — no shared dependency with M13. Loose coupling via clean internal boundaries. M13 can extract a common codec later without breaking this tool.

3. **Diagnostics via didOpen + notification collection** — rather than requiring a separate diagnostics pull API (LSP 3.17+), use the universal notification pattern supported by all language servers.

4. **Synchronous tool interface, async internal I/O** — `Tool.execute()` is synchronous per the `Tool` interface. Internally, `LspClient` uses blocking I/O with timeouts for reading responses. A background virtual thread collects notifications.

5. **Operation-as-parameter design** — instead of separate tool classes per LSP operation, a single `LspTool` with an `operation` parameter keeps registration simple and mirrors how other tools handle multi-mode behavior.

## Alternatives Considered

- **Separate tool per operation** (e.g. `LspHoverTool`, `LspDefinitionTool`) — rejected: would require shared `LspClient` state across tools, complicating lifecycle management.
- **LSP diagnostics pull API** (`textDocument/diagnostic`, LSP 3.17+) — rejected: not universally supported; notification-based approach works with all servers.
- **External JSON-RPC library** — rejected: project minimizes external dependencies; the framing logic is simple enough to implement inline.
