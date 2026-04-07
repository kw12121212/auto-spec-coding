# M9 - LSP 客户端工具

## Goal

实现 Language Server Protocol 客户端工具，使 agent 可通过 LSP 获取代码智能功能。

## In Scope

- LSP 客户端连接管理（启动、初始化、关闭）
- 基础 LSP 操作（go-to-definition, references, hover, diagnostics, document symbols）
- LSP 工具封装（符合 M1 Tool 接口）
- LSP 进程间通信（stdin/stdout JSON-RPC）

## Out of Scope

- 具体 LSP server 的集成（由使用者按语言接入）
- MCP 协议（M10）

## Done Criteria

- LSP 客户端可连接外部 language server 并执行基本操作
- 工具输入/输出符合 M1 Tool 接口
- 有集成测试验证与任意 LSP server 的基本交互

## Planned Changes

- `tool-lsp` - Declared: complete - Language Server Protocol 客户端工具实现

## Dependencies

- M1 核心接口（Tool 接口）
- M2 基础工具集（参考工具实现模式）

## Risks

- LSP 协议版本兼容性（不同 language server 的实现差异）
- LSP 连接的生命周期管理复杂度

## Status

- Declared: complete

## Notes

- LSP 客户端需要支持常见的 language server（jdtls, gopls, typescript-language-server 等）
- 与 M10（MCP）无依赖关系，可并行开发
- LSP 通信使用 stdin/stdout JSON-RPC，可复用 M12 的 JSON-RPC 编解码层
