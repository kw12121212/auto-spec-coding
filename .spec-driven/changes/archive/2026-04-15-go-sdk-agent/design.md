# Design: go-sdk-agent

## Approach

在现有 `go-sdk/specdriven` 包中新增薄 Agent facade。该 facade 只组合并调用已存在的 `Client.RunAgent`、`Client.StopAgent` 和 `Client.GetAgentState`，不复制 HTTP 请求构造、认证、错误解析或重试逻辑。

Agent API 以调用方持有的 `Client` 为基础创建。Run 操作接收 prompt 和可选参数，并转换为现有 `RunAgentRequest`；stop 和 state 操作接收 agent ID，并复用底层 client 的 agent ID 校验和 endpoint 调用行为。所有底层 `APIError`、network error、context cancellation 和 decode error 都应直接返回给调用方，不包裹成新的不透明错误类型。

测试使用 Go `httptest.Server` 或自定义 `http.RoundTripper` 验证外部可观察行为，包括请求路径、方法、JSON body、agent ID query 参数、nil/empty input 校验，以及 context cancellation 后不产生成功结果。

## Key Decisions

- Agent facade 保持薄封装：这样可以复用 `go-sdk-client` 已验证的 HTTP 行为，避免在高层 API 中重新实现认证、重试或错误处理。
- Run options 使用显式可选字段映射到已有 HTTP model：这样 Go 调用方可以只传 prompt，也可以按需设置 system prompt、max turns 和 tool timeout seconds。
- 不在本 change 中引入工具或事件抽象：M20 已将工具和事件拆成独立 planned changes，保持本 change 聚焦 Agent 操作。
- 不要求启动真实 Java 后端：本 change 的目标是高层 Go API 行为，端到端覆盖留给 `go-sdk-tests`。

## Alternatives Considered

- 直接让调用方继续使用底层 `Client.RunAgent`、`Client.StopAgent` 和 `Client.GetAgentState`：这虽然已经可用，但不能满足 roadmap 中“Agent 操作封装”的目标，也会让后续工具和事件 API 缺少统一入口。
- 在 Go SDK 中实现独立 agent runtime：这超出 M20 范围，且 roadmap 明确 Go SDK 依赖 Java 后端，不实现独立运行时。
- 把工具注册、事件订阅和 Agent 操作合并到一个大 change：这会扩大验证面，并绕过 M20 中已经拆分的 planned change 顺序。
