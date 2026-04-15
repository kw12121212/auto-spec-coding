# M21 - TypeScript Client SDK

## Goal

提供 TypeScript/Node.js 客户端 SDK，通过 HTTP REST API 和 JSON-RPC 调用 Java 后端的全部 agent 能力，让 JS/TS 开发者可直接引入使用。

## In Scope

- npm package 发布（可通过 `npm install` 引入）
- HTTP + JSON-RPC 双传输 client 封装与 TypeScript 类型定义
- Agent 操作封装（create、run、stop、状态查询）
- 工具注册与调用封装
- 事件流订阅（SSE 或 polling）
- 统一错误处理与重试机制
- 集成测试（需 Java 后端运行）

## Out of Scope

- 独立 agent 运行时实现（依赖 Java 后端）
- 浏览器端 SDK（首期仅支持 Node.js 运行时）
- TypeScript 语言的 LLM provider 实现
- 具体业务逻辑（SDK 是通用客户端库）

## Done Criteria

- 可通过 `npm install` 引入 SDK 并创建 agent 实例
- Agent run 可正常发送 prompt 并获取响应（HTTP 和 JSON-RPC 两种传输）
- 工具注册后可在 agent 运行时被调用
- 事件流可正确接收 agent 运行时事件
- 错误类型可被调用方正确判断和处理
- 有集成测试覆盖核心操作（需 Java 后端运行）
- TypeScript 类型定义完整，IDE 可正确提示

## Planned Changes
- `ts-sdk-client` - Declared: complete - HTTP + JSON-RPC 双传输 client 封装、TypeScript 类型定义、认证与重试机制
- `ts-sdk-agent` - Declared: planned - Agent 操作封装：create、run、stop、状态查询
- `ts-sdk-tools` - Declared: planned - 工具注册与调用封装
- `ts-sdk-events` - Declared: planned - 事件流订阅封装（SSE/polling）
- `ts-sdk-tests` - Declared: planned - 集成测试（需 Java 后端运行）

## Dependencies

- M13 JSON-RPC 接口（JSON-RPC 传输依赖）
- M14 HTTP REST API（HTTP 传输依赖）
- M16 集成与发布（后端 API 稳定后 SDK 才能锁定接口）

## Risks

- 双传输层（HTTP + JSON-RPC）增加维护复杂度
- REST API 接口变更会导致 SDK breaking change，需与 M14 同步设计
- JSON-RPC over stdin 的 Node.js 进程管理需仔细处理
- npm package 的 ESM/CJS 双格式兼容

## Status

- Declared: proposed

## Notes

- TypeScript SDK 同时支持 HTTP REST API 和 JSON-RPC 两种传输方式
- JSON-RPC 适合本地嵌入场景（spawn Java 进程通过 stdin/stdout 通信），HTTP 适合远程服务场景
- 参考 Java SDK (M12) 的公共 API 设计，保持多语言 SDK 使用体验一致
- 可与 M20 (Go SDK) 并行开发
- 发布为 npm package，用户通过 `npm install @specdriven/sdk` 引入
- 首期仅支持 Node.js 运行时，浏览器端支持可后续扩展

