# background-tool-integ

## What

将后台进程管理器 (`ProcessManager`) 与 Agent 生命周期集成，确保当 Agent 停止时自动清理所有后台进程。

具体实现：
- 在 `DefaultAgent.stop()` 中调用 `ProcessManager.stopAll()` 终止所有活跃后台进程
- 在 `DefaultAgent.close()` 中确保后台进程被清理
- 添加 `ProcessManager` 到 `AgentContext` 的访问路径
- 更新 `SimpleAgentContext` 以支持 `ProcessManager` 注入

## Why

M15 里程碑的前三个变更已完成：
- `background-tool-interface` - BackgroundTool 接口和 BackgroundProcessHandle 类型
- `process-manager` - 后台进程管理器实现
- `server-tool-lifecycle` - Server 类工具的就绪探测和资源清理机制

但后台进程的生命周期目前与 Agent 生命周期是分离的。如果 Agent 停止时后台进程仍在运行，会导致：
1. 资源泄漏 - 僵尸进程持续占用系统资源
2. 端口占用 - Server 类工具可能持续占用端口
3. 不一致状态 - Agent 已停止但关联进程仍在执行

本变更确保后台进程严格绑定到 Agent 会话生命周期。

## Scope

**In Scope:**
- 修改 `DefaultAgent` 在 `stop()` 和 `close()` 时调用 `ProcessManager.stopAll()`
- 修改 `AgentContext` 接口，添加 `processManager()` 方法
- 修改 `SimpleAgentContext` 以支持 `ProcessManager` 注入
- 添加单元测试验证 Agent 停止时后台进程被正确清理

**Out of Scope:**
- 修改 `ProcessManager` 本身的实现（已在 M15 完成）
- 修改 `BackgroundTool` 接口
- 添加新的工具类型
- 跨 session 进程持久化

## Unchanged Behavior

- `ProcessManager` 的 API 和行为保持不变
- `BackgroundTool` 接口不变
- Agent 状态机转换规则不变
- 现有的 Agent 执行流程不变（仅增加清理步骤）
