# Tasks: agent-session-store

## Implementation

- [x] 新增 `Session` record（字段：id、state、createdAt、updatedAt、expiryAt、conversation），expiryAt 在构造时固定为 createdAt + 30 天
- [x] 新增 `SessionStore` 接口（save、load、delete、listActive）
- [x] 新增 `LealoneSessionStore` 实现：
  - [x] 在 `init` 时建表（agent_sessions、agent_messages），若已存在则跳过
  - [x] 实现 `save`：插入或更新 agent_sessions 行，删除旧 agent_messages 并批量插入新行；id 为空时生成 UUID
  - [x] 实现 `load`：按 session_id 查询 agent_sessions + agent_messages，重建 Conversation
  - [x] 实现 `delete`：删除 agent_messages 再删除 agent_sessions（顺序避免孤儿行）
  - [x] 实现 `listActive`：查询 expiry_at > now() 的所有 session
  - [x] 启动后台 VirtualThread 每小时执行 TTL 清理 SQL，异常仅打印警告
- [x] 为 Message 序列化/反序列化写工具方法（JsonObject → Message record，覆盖四种 role）
- [x] 为 `SimpleAgentContext` 新增带 `SessionStore` 参数的构造函数重载（原有构造函数不变）
- [x] 在 `DefaultAgent.doExecute` 中集成 SessionStore：执行前 load，执行后/异常时 save
- [x] 在 `pom.xml` 中确认 `lealone-orm` 依赖已包含（当前已有，无需新增）

## Testing

- [x] 运行 lint/编译检查（lint）：`mvn compile -q`
- [x] 运行 unit test：`mvn test -q`
- [x] 新增 `LealoneSessionStoreTest`：
  - [x] save 后 load 返回等价 Session（含 Conversation 消息历史）
  - [x] 首次 save 时 id 为 null，返回值为非空 UUID 字符串
  - [x] delete 后 load 返回 Optional.empty()
  - [x] listActive 不返回 expiryAt 已过期的 session
  - [x] TTL 清理：构造 expiry_at 为过去时间的行，触发清理后确认已删除
- [x] 新增 `SessionStoreIntegrationTest`：DefaultAgent + LealoneSessionStore 完成一轮 execute，重新 load 后 Conversation 历史与内存版一致

## Verification

- [x] 确认 `SessionStore` 未注入时已有所有测试仍然通过（无回归）
- [x] 确认 `agent_sessions` 和 `agent_messages` 表在首次 init 时自动创建
- [x] 确认 delta spec `specs/agent-interface.md` 中所有 ADDED 需求均有对应实现和测试覆盖
