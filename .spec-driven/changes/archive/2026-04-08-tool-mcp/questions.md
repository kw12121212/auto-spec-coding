# Questions: tool-mcp

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should MCP tool names discovered from external servers be prefixed to avoid collisions with built-in tools?
  Context: If an MCP server exposes a tool named "read", it would clash with the built-in ReadTool in `AgentContext.toolRegistry()`.
  A: Yes — prefix format `mcp__<serverName>__<toolName>`, consistent with Claude Code conventions.

- [x] Q: What MCP protocol version should be targeted?
  Context: The protocol is evolving. Targeting a specific version determines handshake validation strictness.
  A: Target `2024-11-05` (latest stable, widely deployed by mainstream MCP servers).

- [x] Q: Should `McpServer` be implemented in this change, or deferred to a follow-up?
  Context: The MCP server role adds ~30% more work but makes the change more complete.
  A: Include in this change — deliver both client and server roles.
