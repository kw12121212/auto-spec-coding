# Questions: llm-provider-openai

## Open

## Resolved

- [x] Q: Should `ToolMessage` include a `tool_call_id` field for OpenAI compatibility, or map `toolName` to `tool_call_id`?
  **Answer:** Add `toolCallId` field to `ToolMessage` to fix potential bug when the same tool is called multiple times in one turn. This is a scope expansion for this change.

- [x] Q: Should the `JsonWriter`/`JsonReader` utilities be package-private helpers in `agent` package, or a separate `json` utility package?
  **Answer:** Separate subpackage `org.specdriven.agent.json` with public classes, enabling reuse by `llm-provider-claude`.
