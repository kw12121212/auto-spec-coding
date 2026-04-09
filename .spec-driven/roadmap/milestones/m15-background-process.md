# M15 - 后台进程管理

## Goal

为 tool surface 提供后台执行能力和 server 类（长驻）工具的生命周期管理，使 agent 能启动、监控、停止后台进程。

## In Scope

- 扩展 Tool 接口或引入 BackgroundTool 接口，支持异步执行模式
- 后台进程管理器：启动、跟踪、查询、停止后台进程（基于 JDK 25 ProcessHandle）
- Server 类工具的生命周期管理（健康检查、端口探测、资源清理）
- 后台进程输出流式获取
- Agent Stop 时自动清理所有后台进程

## Out of Scope

- 具体工具实现（M2 tool-bash 负责具体 bash 后台执行）
- 容器化或沙箱级别的进程隔离
- 跨 session 进程持久化（进程仅在当前 session 内存活）

## Done Criteria

- 后台进程可在 Tool.execute 中启动并立即返回进程句柄
- ProcessManager 可列出所有活跃后台进程及其状态
- 可通过进程 ID 获取后台进程的累积输出
- 可通过进程 ID 停止指定后台进程
- Server 类工具可通过健康检查确认就绪
- Agent Stop 时所有后台进程被自动终止
- 每项能力有独立单元测试覆盖

## Planned Changes
- `background-tool-interface` - Declared: complete - 定义 BackgroundTool 接口和 BackgroundProcessHandle 类型，扩展 tool surface 异步执行模型
- `process-manager` - Declared: complete - 后台进程管理器实现，跟踪进程生命周期、输出收集、终止控制
- `server-tool-lifecycle` - Declared: complete - Server 类工具的就绪探测和资源清理机制
- `background-tool-integ` - Declared: complete - 与 Agent 生命周期集成，Agent Stop 时清理后台进程

## Dependencies

- M1 核心接口（Tool、Agent 接口）
- M2 tool-bash（bash 工具将使用后台执行能力）
- M4 Agent 生命周期（Agent Stop 触发进程清理）

## Risks

- 僵尸进程：Agent 异常退出时进程未被正确清理
- 端口冲突：多个 server 工具同时启动时端口争用
- 输出缓冲：长时间运行的后台进程输出可能耗尽内存

## Status
- Declared: complete

## Notes

- 使用 JDK 25 ProcessHandle API 增强进程管理能力
- Server 类工具的健康检查策略 SHOULD 可配置（超时、重试次数、探测方式）
- 后台进程的生命周期严格绑定到 agent session，不做跨 session 持久化



