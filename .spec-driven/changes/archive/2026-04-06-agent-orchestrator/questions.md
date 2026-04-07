# Questions: agent-orchestrator

## Open

## Resolved

- [x] Q: LlmClient.chat() 的入参是 `List<Message>` 还是独立的请求类型（可能携带 model、temperature 等参数）？
  Context: 如果入参只是 Message 列表，M5 的 provider 需要另行获取 model 等配置；如果入参是请求对象，则 orchestrator 需要构造请求。这影响 LlmClient 接口设计。
  A: 首期用 `List<Message>`，M5 可扩展为请求对象而不破坏接口。

- [x] Q: ToolCallResponse 中的多个 ToolCall 是否总是并行执行，还是需要某种顺序/依赖机制？
  Context: 当前设计为并行，但实际 LLM 可能返回有依赖关系的工具调用。是否首期就并行，后续再扩展？
  A: 需要顺序/依赖机制。ToolCalls 按 list 顺序串行执行，保证前一个结果可供后续 ToolCall 参考。

- [x] Q: 编排循环中 Tool 执行失败（ToolResult.Error）时，是终止循环还是将错误作为 ToolMessage 反馈给 LLM 继续推理？
  Context: 终止更简单但可能丢失 LLM 自修复机会；反馈给 LLM 更健壮但增加复杂度。
  A: 反馈给 LLM 作为 ToolMessage，让 LLM 有自修复机会。
