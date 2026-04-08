# M12 - Native Java SDK 层

## Goal

在 M1 核心接口之上封装面向第三方的公共 Java SDK facade，让外部可通过 Maven 依赖直接使用 agent 的全部能力。

## In Scope

- 面向第三方的公共 SDK API（facade 层，封装 M1-M10 全部核心能力）
- 事件订阅与回调机制
- 统一错误处理模式
- SDK 使用文档和示例

## Out of Scope

- 非 Java 语言 SDK
- 具体传输层实现（M12-M13）
- 底层接口的重新定义（SDK 是 M1 接口的 facade，不替代它们）

## Done Criteria

- 第三方可通过 Maven 依赖引入 SDK 并创建 agent 实例
- SDK 事件回调可正确接收 agent 运行时事件
- 错误类型可被调用方正确判断和处理
- 有示例代码展示基本用法（创建 agent、注册工具、运行循环）

## Planned Changes
- `sdk-public-api` - Declared: complete - 公共 SDK facade 接口定义与实现 (archived 2026-04-08)
- `sdk-events` - Declared: complete - 事件订阅与回调机制实现 (archived 2026-04-08)
- `sdk-error-handling` - Declared: complete - 统一错误类型与处理模式实现

## Dependencies

- M1-M5 核心能力（接口、工具集、agent 运行时、LLM 后端）
- M6 权限系统（SDK 需暴露权限配置）
- M9-M10 协议工具（LSP/MCP 作为可选工具注册）

## Risks

- SDK API 一旦发布很难做 breaking change，首次设计需充分评审
- 需平衡易用性与灵活性——facade 不应过度隐藏底层能力

## Status
- Declared: complete

## Notes

- SDK 层是 M1 核心接口的 facade，不是替代。底层 M1 接口仍然可用，SDK 提供更高层次的便捷 API
- 参考 spec-coding-sdk 的 Go SDK 使用模式提炼常用操作为简洁的 Java SDK 调用
- M7-M8（注册表）为可选依赖，SDK 可延迟初始化注册表功能
- 发布为 Maven artifact，用户通过 pom.xml / build.gradle 引入



