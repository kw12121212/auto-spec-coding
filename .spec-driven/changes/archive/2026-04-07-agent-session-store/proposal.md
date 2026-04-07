# agent-session-store

## What

实现 `SessionStore` 接口及其 Lealone DB 支持的具体实现，将 Agent 的会话（Session）、对话历史（Conversation）和状态快照持久化到嵌入式数据库，支持崩溃恢复、跨 Session 对话续接，以及按 Session ID 外部查询。

## Why

当前 Agent 运行时（M4 已完成的三个变更）完全基于内存：进程重启后所有会话状态丢失，无法恢复正在进行的对话。`agent-session-store` 解决这一缺陷：
- 赋予 Agent 可恢复性，为 M14 HTTP REST API 的长连接场景提供基础支撑
- 首次在项目中验证 Lealone 嵌入式 DB 的集成模式，为 M6（权限审计）、M7（任务注册表）、M8（定时任务注册表）提供可复用的参考
- 关闭 M4 里程碑剩余工作的第一步（另一步为 event-audit-log）

## Scope

**In Scope：**
- `SessionStore` 接口：`save(Session)`、`load(String sessionId)`、`delete(String sessionId)`、`listActive()`
- `Session` 数据类：包含 session_id（UUID）、agent_state、created_at、updated_at、expiry_at（30 天 TTL）
- Lealone DB 实现 `LealoneSessionStore`：两张表
  - `agent_sessions`：结构化列（id、state、created_at、updated_at、expiry_at）
  - `agent_messages`：每条 Message 一行，content 以 JSON 字符串存 CLOB 列
- Session ID 由 SDK 自动生成 UUID；`save` 在首次保存时填充并返回 ID
- TTL 自动清理：30 天未更新的 Session 被定期清除
- `SimpleAgentContext` 集成：构造时可注入 `SessionStore`，`execute` 开始时 load，状态变更时 save

**Out of Scope：**
- 跨节点分布式 Session 同步
- Session 加密存储（属于 M18 密钥保险库）
- HTTP/REST 层的 Session 管理（属于 M14）
- `event-audit-log`（同属 M4 的独立后续变更）

## Unchanged Behavior

- `DefaultAgent` 状态机转换逻辑不变
- `DefaultOrchestrator` 编排循环逻辑不变
- `Conversation` 的内存操作语义不变
- 未注入 `SessionStore` 时，Agent 行为与现在完全一致（SessionStore 为可选依赖）
