# Tasks: tool-lsp

## Implementation

- [x] Implement `LspClient` with JSON-RPC 2.0 transport (Content-Length framing, request/response correlation, notification handling)
- [x] Implement `LspClient` lifecycle (launch subprocess, initialize handshake, shutdown/exit, process cleanup)
- [x] Implement `LspClient` textDocument operations: didOpen, didClose, hover, definition, references, documentSymbol
- [x] Implement `LspClient` diagnostics collection (notification listener, publishDiagnostics handler)
- [x] Implement `LspTool` implementing `Tool` interface with operation dispatch
- [x] Wire `LspTool` permission declaration (`action="execute"`, `resource="lsp"`, constraints containing operation and file)

## Testing

- [x] Run `mvn compile` — verify project compiles
- [x] Run `mvn test` — verify all tests pass including new ones
- [x] Unit test: `LspClient` JSON-RPC message framing (Content-Length header encoding/decoding)
- [x] Unit test: `LspClient` request/response correlation with integer IDs
- [x] Unit test: `LspTool` parameter validation (missing operation, invalid operation, missing file)
- [x] Unit test: `LspTool` dispatch correctness for each operation
- [x] Unit test: `LspClient` lifecycle (initialize handshake, shutdown sequence)
- [x] Unit test: `LspClient` error handling (process failure, timeout, unexpected exit)

## Verification

- [x] Verify implementation matches proposal scope (diagnostics priority, 5 operations total)
- [x] Verify `LspTool` conforms to `Tool` interface contract
- [x] Verify JSON-RPC layer is self-contained (no shared types exposed outside LspClient)
