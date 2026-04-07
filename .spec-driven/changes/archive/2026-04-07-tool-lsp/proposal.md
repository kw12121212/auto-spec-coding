# tool-lsp

## What

Implement a Language Server Protocol client tool (`LspTool`) that enables the agent to connect to external language servers and obtain code intelligence, with primary focus on syntax validation (diagnostics). The tool wraps LSP operations behind the M1 `Tool` interface so the orchestrator can invoke them like any other tool.

## Why

Syntax validation is the highest-value LSP capability for an automated coding agent. Real-time diagnostics (errors, warnings) let the agent detect problems without running a build or test suite, reducing feedback latency and token cost. Supporting operations (hover, go-to-definition) provide secondary value for understanding code context.

## Scope

- LSP client lifecycle: launch language server process, perform LSP initialize handshake, handle shutdown/exit
- JSON-RPC 2.0 message framing over stdin/stdout (standalone implementation, loosely coupled with future M13 JSON-RPC layer)
- LSP operations:
  - `diagnostics` — subscribe to and retrieve diagnostic notifications (primary focus)
  - `hover` — get type/signature info at a position
  - `goToDefinition` — resolve definition location for a symbol at a position
  - `references` — find all references to a symbol at a position
  - `documentSymbols` — list symbols in a document
- `LspTool` implementing the `Tool` interface with operation selected via input parameter
- `LspClient` managing a single language server connection with process lifecycle

## Unchanged Behavior

- Existing tools (BashTool, file ops, GrepTool, GlobTool) are not modified
- Tool interface, ToolInput, ToolResult, ToolContext remain unchanged
- Permission model and hook system remain unchanged
- No changes to agent lifecycle, orchestrator, or event system
