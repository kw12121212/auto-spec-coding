# M20 - Go Client SDK

## Goal

提供 Go 语言客户端 SDK，通过 HTTP REST API 调用 Java 后端的全部 agent 能力，让 Go 开发者可直接引入使用。

## In Scope

- Go module 发布（可通过 `go get` 引入）
- HTTP client 封装与类型定义（请求/响应模型）
- Agent 操作封装（create、run、stop、状态查询）
- 工具注册与调用封装
- 事件流订阅（SSE 或 polling）
- 统一错误处理与重试机制
- 集成测试（需 Java 后端运行）

## Out of Scope

- 独立 agent 运行时实现（依赖 Java 后端）
- JSON-RPC 传输（仅走 HTTP REST API）
- Go 语言的 LLM provider 实现
- 具体业务逻辑（SDK 是通用客户端库）

## Done Criteria

- 可通过 `go get` 引入 SDK 并创建 agent 实例
- Agent run 可正常发送 prompt 并获取响应
- 工具注册后可在 agent 运行时被调用
- 事件流可正确接收 agent 运行时事件
- 错误类型可被调用方正确判断和处理
- 有集成测试覆盖核心操作（需 Java 后端运行）

## Planned Changes
- `go-sdk-client` - Declared: complete - HTTP client 封装、Go 类型定义、请求/响应模型、认证与重试机制
- `go-sdk-agent` - Declared: planned - Agent 操作封装：create、run、stop、状态查询
- `go-sdk-tools` - Declared: planned - 工具注册与调用封装
- `go-sdk-events` - Declared: planned - 事件流订阅封装（SSE/polling）
- `go-sdk-tests` - Declared: planned - 集成测试（需 Java 后端运行）

## Dependencies

- M14 HTTP REST API（SDK 通过 REST API 与后端通信）
- M16 集成与发布（后端 API 稳定后 SDK 才能锁定接口）

## Risks

- REST API 接口变更会导致 SDK breaking change，需与 M14 同步设计
- 事件流的长连接稳定性需验证
- Go SDK 的 API 设计需兼顾惯用 Go 风格与 Java 后端 API 的一致性

## Status

- Declared: proposed

## Notes

- Go SDK 仅通过 HTTP REST API 通信，不依赖 JSON-RPC
- 参考 Java SDK (M12) 的公共 API 设计，保持多语言 SDK 使用体验一致
- 可与 M21 (TypeScript SDK) 并行开发
- 发布为 Go module，用户通过 `go get github.com/.../specdriven-go` 引入

