# Design: llm-provider-interface

## Approach

在现有 `org.specdriven.agent.agent` 包中引入 LLM 抽象层，采用接口+record 的风格与现有代码一致：

1. **LlmProvider 接口** — provider 生命周期管理，工厂方法创建 LlmClient 实例
2. **LlmConfig record** — 不可变配置，支持从 Map<String,String> 构建（与 AgentContext.config() 兼容）
3. **LlmRequest record** — 封装完整请求参数（messages、tools、system prompt、temperature 等）
4. **增强 LlmResponse** — 扩展现有 sealed interface，增加 usage 和 finishReason
5. **ToolSchema record** — 将 Tool 接口元数据转换为 LLM 可理解的 tool definition 格式
6. **LlmStreamCallback 接口** — 流式 token 回调，为后续 streaming 实现预留

### 类型层次

```
LlmProvider (interface)
  ├── LlmConfig config()
  ├── LlmClient createClient()
  └── void close()

LlmClient (enhanced interface)
  ├── LlmResponse chat(LlmRequest request)
  └── void chatStreaming(LlmRequest request, LlmStreamCallback callback)

LlmRequest (record)
  ├── List<Message> messages()
  ├── String systemPrompt()
  ├── List<ToolSchema> tools()
  ├── double temperature()
  └── int maxTokens()

LlmResponse (enhanced sealed interface)
  ├── TextResponse(content, usage, finishReason)
  └── ToolCallResponse(toolCalls, usage, finishReason)

LlmStreamCallback (interface)
  ├── void onToken(String token)
  ├── void onComplete(LlmResponse response)
  └── void onError(Exception e)

ToolSchema (record)
  ├── String name()
  ├── String description()
  └── Map<String, Object> parameters()  // JSON Schema 格式

LlmUsage (record)
  ├── int promptTokens()
  ├── int completionTokens()
  └── int totalTokens()
```

## Key Decisions

1. **向后兼容 LlmClient.chat(List<Message>)** — 保留现有方法签名，新增 `chat(LlmRequest)` 重载。DefaultOrchestrator 无需修改即可继续工作，后续可渐进迁移到新 API。
2. **ToolSchema 独立于 Tool** — ToolSchema 是 LLM 层的类型，Tool 是 agent 层的类型。通过 `ToolSchema.from(Tool)` 工厂方法做转换，保持两层解耦。
3. **LlmConfig 从 Map 构建** — 与 AgentContext.config() 返回类型一致，可直接透传配置。
4. **不引入 JSON 依赖** — ToolSchema.parameters() 使用 Map<String,Object>，与现有 Event.metadata() 风格一致。后续 provider 实现负责序列化为 JSON。

## Alternatives Considered

1. **用泛型参数化 LlmClient<LlmMessage>** — 放弃：现有代码已用 Message sealed interface，引入泛型会增加所有调用方的复杂度，且 provider 差异在序列化层而非类型层。
2. **LlmRequest 使用 Builder 模式而非 record** — 放弃：record 的 `withXxx()` 方法足够处理不可变构建，且与项目现有 record 风格一致。
3. **将 LLM 类型放在独立 package（如 llm）** — 放弃：LlmClient 已在 agent 包中，移动会产生不必要的 refactor，保持聚合。
