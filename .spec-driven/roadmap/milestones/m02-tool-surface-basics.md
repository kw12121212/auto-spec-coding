# M02 - Tool Surface 基础工具集

## Goal

实现 bash、文件操作、grep、glob 四大核心工具，构成 agent 可调用的基础 tool surface。

## In Scope

- Bash 执行工具（命令运行、超时、输出捕获，使用 Java ProcessBuilder / ProcessHandle）
- 文件读/写/编辑工具（使用 java.nio.file API）
- 内容搜索工具（基于 Java NIO Files.walk + 正则匹配，或 ripgrep 二进制调用）
- 文件模式匹配工具（基于 java.nio.file.PathMatcher / glob pattern）
- 工具级别预留权限检查钩子（基于 M01 PermissionProvider 接口）

## Out of Scope

- 内置外部工具管理（M03）
- LSP 客户端工具（M09）
- MCP 协议工具（M10）
- 权限策略的具体执行逻辑（M06）

## Done Criteria

- 每个工具可独立实例化并执行基本操作
- 每个工具有对应的单元测试覆盖 happy path 和 error case
- 工具输入/输出符合 M01 定义的 Tool 接口
- 每个工具在执行前可通过 PermissionProvider 钩子进行权限检查

## Planned Changes

- `tool-bash` - Declared: complete - Bash 命令执行工具实现（ProcessBuilder + 超时控制）
- `tool-file-ops` - Declared: complete - 文件读/写/编辑工具实现（java.nio.file）
- `tool-grep` - Declared: complete - 内容搜索工具实现（Java NIO 或 ripgrep 调用）
- `tool-glob` - Declared: complete - 文件模式匹配工具实现（PathMatcher）

## Dependencies

- M01 核心接口（Tool 接口、PermissionProvider 接口）

## Risks

- Bash 工具的安全性约束需要仔细设计，避免命令注入
- 文件编辑操作的原子性和并发安全

## Status

- Declared: complete



## Notes

- 工具行为应与 spec-coding-sdk 的 Go 实现保持功能对等
- tool-grep 优先使用 Java NIO 实现基础搜索；如需 ripgrep 高性能，由 M03 的工具管理器提供二进制
- 使用 JDK 25 的 ProcessHandle API 增强进程管理能力
