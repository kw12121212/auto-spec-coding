# Design: agent-session-store

## Approach

### 数据模型

两张 Lealone SQL 表：

```sql
CREATE TABLE IF NOT EXISTS agent_sessions (
    id         VARCHAR(36)  PRIMARY KEY,
    state      VARCHAR(20)  NOT NULL,
    created_at BIGINT       NOT NULL,
    updated_at BIGINT       NOT NULL,
    expiry_at  BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_messages (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    content    CLOB         NOT NULL,
    tool_name  VARCHAR(255),
    ts         BIGINT       NOT NULL
);
```

- `agent_sessions.state` 存储 `AgentState.name()`
- `agent_messages.content` 存储 Message 内容的 JSON 字符串（通过 Lealone ORM `JsonObject` 序列化）
- `agent_messages.tool_name` 仅 ToolMessage 时填充，其余为 NULL

### Session ID

`LealoneSessionStore.save(Session)` 在 session.id 为空时自动生成 `UUID.randomUUID().toString()`，填入 Session 对象并返回。调用方可从返回值中获取 ID，用于后续 `load`/`delete`。

### TTL 清理

`LealoneSessionStore` 初始化时启动一个 Java VirtualThread 后台任务，每小时执行一次：
```sql
DELETE FROM agent_messages WHERE session_id IN (
    SELECT id FROM agent_sessions WHERE expiry_at < ?
);
DELETE FROM agent_sessions WHERE expiry_at < ?;
```
TTL 为 30 天（`expiry_at = created_at + 30 * 24 * 3600 * 1000L`），每次 `save` 时刷新 `updated_at`，但不重置 `expiry_at`（TTL 从首次创建起算）。

### SimpleAgentContext 集成

`SimpleAgentContext` 新增可选 `SessionStore` 字段（构造函数重载，无 SessionStore 的构造函数保持不变）。`DefaultAgent.doExecute` 在调用 orchestrator 前调用 `store.load(sessionId)` 恢复 Conversation，结束后调用 `store.save(session)`。

## Key Decisions

1. **两张表而非单张表 + JSON blob**：`agent_sessions` 结构化列支持按 state、expiry_at 过滤查询；messages 单独成表支持按 session_id 检索完整历史，同时避免大 CLOB 单行读取。

2. **TTL 从创建起算，不随 save 重置**：防止活跃 Agent 无限延长会话生命周期导致 DB 膨胀；如需续期，由调用方显式操作（超出本次范围）。

3. **SessionStore 为可选依赖**：不注入时 Agent 行为退化为纯内存模式，保持向后兼容，不影响已有测试。

4. **VirtualThread 后台清理**：与项目其他并发模型保持一致（M4 notes 明确使用 JDK 25 VirtualThread）；清理失败不影响主路径，仅打印警告日志。

5. **CLOB 而非 VARCHAR(MAX)**：Message content 可能较长（LLM 输出、工具返回），CLOB 无长度上限且 Lealone 支持。

## Alternatives Considered

- **单张表 + messages 作为整体 CLOB**：读写简单，但无法单独查询消息条数、角色分布，也难以做增量追加；放弃。
- **Java 序列化（ObjectOutputStream）**：紧凑但不可读、版本不兼容风险高；放弃，选择 JSON 字符串。
- **定时清理改用 Lealone 内置调度**：M8（定时任务注册表）尚未实现，不可提前依赖；本次用 VirtualThread sleep 循环替代，M8 完成后可替换。
