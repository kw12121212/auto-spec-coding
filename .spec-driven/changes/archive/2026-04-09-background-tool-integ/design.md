# Design: background-tool-integ

## Approach

1. **扩展 AgentContext 接口**
   - 添加 `processManager()` 方法返回 `Optional<ProcessManager>`
   - 使用 Optional 避免破坏现有实现，保持向后兼容

2. **修改 SimpleAgentContext**
   - 添加 `ProcessManager` 字段和构造函数参数
   - 实现 `processManager()` 返回非空 Optional

3. **修改 DefaultAgent**
   - 在 `stop()` 方法中，如果 `AgentContext` 提供 `ProcessManager`，则调用 `stopAll()`
   - 在 `close()` 方法中同样执行清理（作为保险机制）
   - 清理操作在状态转换之后执行，确保即使清理失败 Agent 仍能进入 STOPPED 状态

4. **错误处理策略**
   - 后台进程清理失败不应阻止 Agent 状态转换
   - 清理异常应被捕获并记录（通过 EventBus 发送事件）

## Key Decisions

1. **Optional<ProcessManager> vs 直接引用**
   - 选择 Optional 包装以保持 AgentContext 接口的向后兼容性
   - 现有代码无需修改即可继续工作

2. **在 stop() 中清理而非 close()**
   - 在 `stop()` 中执行主要清理，因为 `stop()` 是生命周期中的标准停止点
   - `close()` 作为保险机制也执行清理，处理直接调用 close() 跳过 stop() 的场景

3. **不修改现有构造函数签名**
   - `SimpleAgentContext` 添加新的构造函数重载，保留旧构造函数
   - 避免破坏现有调用方

4. **清理失败不阻塞状态转换**
   - 使用 try-catch 包裹清理逻辑
   - 确保 Agent 总能进入 STOPPED 状态

## Alternatives Considered

1. **在 Agent 中持有 ProcessManager 引用**
   - 被拒绝：Agent 不应直接依赖 ProcessManager，通过 Context 解耦更符合设计

2. **在 Orchestrator 中清理**
   - 被拒绝：Orchestrator 负责编排循环，生命周期管理是 Agent 的职责

3. **强制要求 ProcessManager**
   - 被拒绝：使用 Optional 更灵活，允许不使用后台工具的 Agent 无需提供 ProcessManager
