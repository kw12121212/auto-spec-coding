# M3 - 内置外部工具集成

## Goal

集成 ripgrep、GitHub CLI 等常用外部工具，通过内置工具管理器提供预编译二进制的自动检测、下载与安装能力。

## In Scope

- 内置工具管理器（外部工具检测、预编译二进制下载与安装）
- ripgrep 集成（供 M2 tool-grep 高性能模式使用）
- GitHub CLI（gh）集成工具

## Out of Scope

- 核心工具接口实现（M2）
- 源码编译安装——所有外部工具仅下载预编译二进制
- LSP / MCP 工具（M9 / M10）

## Done Criteria

- 内置工具管理器可检测系统已安装的外部工具
- 缺失的外部工具可被自动下载预编译二进制并安装
- ripgrep 可通过工具管理器获取并供 M2 tool-grep 使用
- gh 工具可被 agent 调用执行基本 GitHub 操作

## Planned Changes

- `builtin-tool-manager` - Declared: planned - 内置工具管理器，自动检测、下载预编译二进制并安装外部工具
- `tool-gh` - Declared: planned - GitHub CLI 集成工具实现

## Dependencies

- M1 核心接口（Tool 接口、PermissionProvider 接口）
- M2 tool-grep 依赖此里程碑提供的 ripgrep 二进制（可选增强）

## Risks

- 依赖上游提供各平台预编译二进制，平台覆盖不全时需降级处理
- 工具管理器的网络下载需要处理代理、超时、校验等边界情况

## Status

- Declared: proposed

## Notes

- 所有外部工具仅下载预编译二进制，不从源码编译
- 预编译二进制来源：ripgrep（GitHub Releases）、gh（GitHub Releases）
- builtin-tool-manager 应作为此里程碑的基础设施先行完成，tool-gh 依赖它
- HTTP 下载使用 Lealone 的异步网络模块（lealone-net）
