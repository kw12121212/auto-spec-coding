# go-sdk-agent

## What

为 M20 Go Client SDK 增加高层 Agent 操作封装，使 Go 调用方可以基于已完成的底层 HTTP client 创建 Agent handle，并通过惯用 Go API 执行 prompt、停止 agent、查询状态。

该 change 覆盖：

- 基于 `specdriven.Client` 的 Agent handle 构造入口
- Agent run 封装，支持 prompt、system prompt、max turns、tool timeout seconds 等现有 HTTP API 参数
- Agent stop 封装
- Agent state 查询封装
- 对空 prompt、空 agent ID、nil client、context cancellation 等调用错误或传输错误的清晰返回行为
- 对已有 typed API error 和 retry 行为的透传

## Why

`go-sdk-client` 已经提供 Go module、底层 HTTP endpoint 方法、请求/响应模型、认证、错误处理和重试机制。M20 的下一个依赖顺序应当是在该基础上提供更符合 Go SDK 使用习惯的 Agent 层 API，而不是要求调用方直接拼接所有底层 endpoint 调用。

先实现 `go-sdk-agent` 可以让 Go SDK 具备最核心的 agent 操作体验，并为后续 `go-sdk-tools`、`go-sdk-events` 和 `go-sdk-tests` 提供稳定的上层调用入口。

## Scope

In scope:

- 在 `go-sdk/specdriven` 包内新增 Agent 操作封装
- 提供从现有 `Client` 创建 Agent handle 的公开入口
- 提供 run API，将 Go 调用方的 prompt 与可选 run options 转换为现有 `RunAgentRequest`
- 提供 stop API，调用现有 `Client.StopAgent`
- 提供 state API，调用现有 `Client.GetAgentState`
- 保持现有 `Client` endpoint 方法可直接使用
- 使用 Go unit tests 覆盖 Agent 构造、run 参数传递、stop/state agent ID 处理、error 透传和 context cancellation

Out of scope:

- 不实现独立 Go agent runtime
- 不新增或修改 Java HTTP REST API
- 不实现 Go 侧工具注册/调用高层封装；该能力留给 `go-sdk-tools`
- 不实现 SSE/polling 事件订阅；该能力留给 `go-sdk-events`
- 不实现真实 Java 后端集成测试；该能力留给 `go-sdk-tests`
- 不实现 JSON-RPC 传输

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- 现有 `specdriven.Client` 构造、认证、重试、错误处理和底层 endpoint 方法保持向后兼容
- 现有 Java SDK、JSON-RPC、HTTP REST API 的请求路径、响应格式和错误格式保持不变
- Go SDK 不改变 Java 后端 agent 生命周期、工具执行、权限、认证或限流语义
