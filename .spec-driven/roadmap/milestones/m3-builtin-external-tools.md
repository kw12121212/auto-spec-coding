# M3 - 内置外部工具集成

## Goal

集成 ripgrep、fd 等常用外部工具，通过内置工具管理器提供预编译二进制的自动检测与提取能力。

## In Scope

- 内置工具管理器（外部工具检测、预编译二进制从 classpath 提取）
- ripgrep 集成（供 M2 tool-grep 高性能模式使用）
- fd 集成（替代 M2 GlobTool 纯 Java 实现，纯 Java 作为 fallback）

## Out of Scope

- 核心工具接口实现（M2）
- 源码编译安装——所有外部工具仅下载预编译二进制
- LSP / MCP 工具（M9 / M10）

## Done Criteria

- 内置工具管理器可检测系统已安装的外部工具
- 缺失的外部工具可被自动从 classpath 提取预编译二进制到缓存
- ripgrep 可通过工具管理器获取并供 M2 tool-grep 使用
- fd 可通过工具管理器获取并供 GlobTool 使用，fd 不可用时回退到纯 Java 实现

## Planned Changes
- `builtin-tool-manager` - Declared: complete - 内置工具管理器，自动检测、从 classpath 提取预编译二进制到本地缓存
- `tool-fd` - Declared: complete - fd 集成工具，使用 fd 二进制提供高性能文件查找，GlobTool 纯 Java 实现作为 fallback

## Dependencies

- M1 核心接口（Tool 接口、PermissionProvider 接口）
- M2 tool-grep 依赖此里程碑提供的 ripgrep 二进制（可选增强）

## Risks

- 依赖上游提供各平台预编译二进制，平台覆盖不全时需降级处理
- 工具管理器的网络下载需要处理代理、超时、校验等边界情况

## Status
- Declared: complete

## Notes

- 所有外部工具仅下载预编译二进制，不从源码编译
- 预编译二进制来源：ripgrep（GitHub Releases）、gh（GitHub Releases）、fd（GitHub Releases）
- builtin-tool-manager 应作为此里程碑的基础设施先行完成，tool-gh 和 tool-fd 均依赖它
- HTTP 下载使用 Lealone 的异步网络模块（lealone-net）



