# go-sdk-client

## What

新增 Go Client SDK 的首个基础 change：在仓库内提供一个可独立测试的 Go module，封装 Java 后端 HTTP REST API 的底层客户端能力。

该 change 覆盖：

- Go SDK module 与公开 client 构造入口
- HTTP base URL、认证 token/API key、超时与重试配置
- 与现有 HTTP REST API 对齐的请求/响应类型
- `GET /api/v1/health`
- `GET /api/v1/tools`
- `POST /api/v1/agent/run`
- `POST /api/v1/agent/stop?id=<agentId>`
- `GET /api/v1/agent/state?id=<agentId>`
- HTTP 状态码、错误响应、网络错误和可重试错误的统一 Go 错误类型

## Why

M20 的目标是让 Go 开发者通过 HTTP REST API 调用 Java 后端 agent 能力。`go-sdk-client` 是该里程碑的第一步，先建立稳定的底层 HTTP client、类型模型和错误语义，后续 `go-sdk-agent`、`go-sdk-tools`、`go-sdk-events` 可以在此基础上提供更高层的惯用 Go API。

先做底层 client 能减少后续 change 的重复工作，并把 REST API 契约差异集中暴露在一个较小的实现面内。

## Scope

In scope:

- 在仓库中新增 Go SDK module，首期模块路径采用当前仓库 SCM 派生路径 `github.com/kw12121212/auto-spec-coding/go-sdk`
- 提供可配置的 HTTP client 构造方式，包括 base URL、认证凭证、超时、重试次数和重试等待策略
- 提供与现有 Java HTTP API 对齐的 Go 请求/响应模型
- 提供底层 endpoint 方法：health、tools、run agent、stop agent、get agent state
- 自动发送受支持的认证 header
- 对 Java HTTP API 的 `ErrorResponse` 返回 typed Go error
- 对网络失败、5xx、429 等场景提供受控重试
- 使用 Go unit tests 覆盖序列化、认证、错误处理和重试行为

Out of scope:

- 不实现独立 Go agent runtime
- 不实现 JSON-RPC 传输
- 不实现 Go 侧 LLM provider
- 不实现高层 Agent facade；该能力留给 `go-sdk-agent`
- 不实现工具注册/调用高层封装；该能力留给 `go-sdk-tools`
- 不实现 SSE/polling 事件订阅；该能力留给 `go-sdk-events`
- 不要求启动真实 Java 后端做端到端集成测试；该能力留给 `go-sdk-tests`

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- 现有 Java SDK、JSON-RPC、HTTP REST API 的请求路径、响应格式和错误格式保持不变
- 现有 Maven 构建与 Java 测试入口保持可用
- Go SDK 不改变 Java 后端认证、限流或 agent 生命周期语义
