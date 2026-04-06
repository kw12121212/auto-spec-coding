# M13 - JSON-RPC 接口

## Goal

实现基于 stdin/stdout 的 JSON-RPC 传输层，提供进程内嵌式接口。

## In Scope

- JSON-RPC 2.0 协议定义（请求、响应、通知、错误）
- stdin/stdout 传输实现
- 请求路由与处理（将 JSON-RPC 调用映射到 SDK 操作）
- JSON-RPC 层的端到端测试

## Out of Scope

- HTTP 传输（M13）
- 批量请求（JSON-RPC 2.0 规范要求但复杂度高，纳入后续迭代）

## Done Criteria

- 可通过 stdin 发送 JSON-RPC 请求并从 stdout 收到正确响应
- 支持通知（单向消息）
- 错误场景返回符合 JSON-RPC 2.0 规范的错误码
- 有端到端测试验证完整请求-响应流程

## Planned Changes

- `jsonrpc-protocol` - Declared: planned - JSON-RPC 2.0 协议类型与编解码实现
- `jsonrpc-transport` - Declared: planned - stdin/stdout 传输层实现
- `jsonrpc-handlers` - Declared: planned - 请求路由与 SDK 操作映射实现
- `jsonrpc-e2e-tests` - Declared: planned - JSON-RPC 层端到端测试实现

## Dependencies

- M11 Native Java SDK 层

## Risks

- stdin/stdout 的缓冲和分隔策略需要仔细处理
- 大量并发请求时的背压控制

## Status

- Declared: proposed

## Notes

- JSON-RPC 接口是 CLI 嵌入场景的首选方式，需与 spec-coding-sdk 的 stdin 协议保持兼容
- 批量请求暂不实现，但协议层需预留批量消息的解析能力
- JSON 处理使用 Lealone 内置的 JSON 能力或 JDK 内置 API，不引入 Jackson/Gson
