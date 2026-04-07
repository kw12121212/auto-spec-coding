# Questions: llm-provider-openai

## Open

- [ ] Q: Should `ToolMessage` include a `tool_call_id` field for OpenAI compatibility, or map `toolName` to `tool_call_id`?
  Context: OpenAI requires `tool_call_id` in tool response messages. The current `ToolMessage` has `toolName` but no call ID. The Claude provider will have the same issue. Options: (a) map `toolName` to `tool_call_id` string, (b) add `toolCallId` to `ToolMessage` as an interface change.

- [ ] Q: Should the `JsonWriter`/`JsonReader` utilities be package-private helpers in `agent` package, or a separate `json` utility package?
  Context: These are implementation details of the OpenAI provider. Making them package-private keeps them hidden but prevents reuse by the Claude provider later. A shared utility package avoids duplication.

## Resolved
