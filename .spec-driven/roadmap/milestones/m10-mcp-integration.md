# M10 - MCP 协议适配

## Goal

实现 Model Context Protocol 适配层，使 agent 可通过 MCP 注册和调用外部工具、暴露资源。

## In Scope

- MCP 协议层定义（工具注册、资源暴露、提示模板）
- MCP 客户端（连接外部 MCP server、调用工具）
- MCP 服务端（暴露 agent 能力为 MCP 工具）
- MCP 工具封装（符合 M1 Tool 接口）

## Out of Scope

- MCP 传输层实现（JSON-RPC / SSE）—— 本里程碑仅定义协议层
- LSP 集成（M9）

## Done Criteria

- MCP 协议层可注册和调用工具
- MCP 客户端可连接外部 MCP server
- 工具输入/输出符合 M1 Tool 接口
- 有单元测试覆盖协议编解码和工具调用

## Planned Changes

- `tool-mcp` - Declared: complete - Model Context Protocol 适配层实现

## Dependencies

- M1 核心接口（Tool 接口）
- M2 基础工具集（参考工具实现模式）

## Risks

- MCP 协议仍在快速演进，API 可能需要频繁调整
- MCP server/client 的能力协商复杂度

## Status

- Declared: complete

## Notes

- MCP 集成参考 spec-coding-sdk 的 MCP 实现模式
- 与 M9（LSP）无依赖关系，可并行开发
