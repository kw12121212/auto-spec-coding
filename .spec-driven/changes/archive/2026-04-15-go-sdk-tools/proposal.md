# go-sdk-tools

## What

为 M20 Go Client SDK 增加工具注册与调用封装，使 Go 调用方可以把 Go 侧实现的工具注册到 Java 后端，并让后端 agent 在正常工具执行流程中通过 HTTP callback 调用这些远程工具。

该 change 覆盖：

- Java HTTP REST API 的远程工具注册入口
- callback-backed remote tool 在后端工具列表和 agent run 中的可见性
- Go SDK 的 tool definition、tool handler、registration facade 和 callback HTTP handler
- Go SDK 对工具 callback invocation request/response 的编码、解码和错误返回
- 与现有 `Client`、`Agent`、认证、重试和错误模型的兼容

## Why

`go-sdk-client` 已提供底层 HTTP client，`go-sdk-agent` 已提供高层 Agent 操作封装。M20 的下一个依赖顺序是让 Go 调用方不仅能运行 prompt，还能把 Go 代码实现的工具暴露给 Java 后端 agent 使用。

当前 HTTP REST API 只支持 `GET /api/v1/tools` 列出已有工具，不支持 Go SDK 远程注册工具或让后端调用 Go 侧工具实现。因此本 change 需要一个最小跨层契约：Go SDK 负责托管工具 callback handler，并通过 Java HTTP API 注册 callback-backed tool；Java 后端在 agent 正常工具执行路径中调用该 callback，而不是新增一个可绕过 agent/permission/orchestrator 语义的通用工具直连执行入口。

## Scope

In scope:

- 在 Java HTTP API 中新增远程工具注册请求/响应模型和 `POST /api/v1/tools/register` 路由
- 注册 callback-backed remote tool 后，`GET /api/v1/tools` MUST 返回该工具元数据
- 后端 agent run 触发该工具调用时，系统 MUST 调用注册时提供的 callback URL，并将 callback 响应转成正常 `ToolResult`
- Go SDK 提供 tool definition、parameter descriptor、tool handler 和 callback response 模型
- Go SDK 提供 tools facade，从现有 `Client` 构造并调用远程工具注册 endpoint
- Go SDK 提供可挂载到 `net/http` 的 callback handler，用于接收 Java 后端的工具 invocation request
- Go unit tests 覆盖 tools facade、registration request、callback handler、handler error 和 context cancellation
- Java unit tests 覆盖 HTTP registration route、tool listing visibility、remote tool callback success/failure 和 agent tool execution path

Out of scope:

- 不实现独立 Go agent runtime
- 不实现 JSON-RPC 传输的远程工具注册
- 不新增通用 `POST /tools/invoke` 之类的直接执行任意后端工具 endpoint
- 不允许远程注册覆盖已有内置工具名称
- 不实现 callback 服务的进程托管、TLS 证书管理、端口发现或公网暴露
- 不实现 SSE/polling 事件订阅；该能力留给 `go-sdk-events`
- 不实现真实 Java 后端集成测试矩阵；该能力留给 `go-sdk-tests`

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- 现有 Go SDK `Client`、`Agent`、认证、重试、错误处理和 endpoint 方法保持向后兼容
- 现有 Java HTTP API 路径和响应格式保持兼容；新增路由不得改变 `/health`、`/tools`、`/agent/run`、`/agent/stop`、`/agent/state` 行为
- 现有 Java SDK tool registration via `SdkBuilder.registerTool` 保持可用
- 现有 agent orchestration, tool permission hooks, tool error feedback, and conversation behavior remain governed by existing requirements
- Go SDK 不改变 Java 后端认证、限流、agent 生命周期或已有 tool execution semantics
