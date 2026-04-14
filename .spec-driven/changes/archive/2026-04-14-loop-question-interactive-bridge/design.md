# Design: loop-question-interactive-bridge

## Approach

在 DefaultLoopDriver 现有人工升级暂停路径中插入交互模式入口：

1. **InteractiveSessionFactory 函数式接口** — 定义 `InteractiveSession create(String sessionId)` 方法，作为 DefaultLoopDriver 的新可选构造参数。LoopDriver 不直接依赖 LealoneAgentAdapter 或任何具体实现。

2. **暂停时创建 session** — 当循环因人工升级问题进入 PAUSED 状态后，若 InteractiveSessionFactory 已配置，立即通过工厂创建新的 InteractiveSession 并 start()。交互 session 的生命周期限定在单次暂停周期内。

3. **交互期间阻塞调度线程** — 调度 VirtualThread 在创建 session 后进入等待状态，定期检查 session 是否已关闭或 loop 是否已被 stop。

4. **通过 QuestionDeliveryService 路由答复** — 交互输入不绕过现有 Question/Answer 模型。当用户在交互 session 中提交内容时，通过 QuestionDeliveryService.submitReply() 将答复绑定到等待中的 Question。

5. **交互结束后恢复循环** — 当 session 关闭（用户主动关闭或进入 ERROR 状态），调度线程被唤醒，调用既有 resume() 路径恢复循环。

6. **审计记录** — 每次进入/退出交互模式发布对应 EventType 事件，交互操作复用已有 EventBus 审计路径。

## Key Decisions

- **每次暂停新建 session**：InteractiveSession 生命周期不可重启，每次暂停创建新实例符合现有生命周期设计，且审计边界清晰。不扩展 InteractiveSession 接口。
- **无交互超时**：首期交互模式仅面向本地操作者，资源泄漏风险可控。超时行为可后续以最小变更添加。
- **工厂模式注入**：DefaultLoopDriver 通过构造参数接收 InteractiveSessionFactory，不直接依赖具体实现类，保持可测试性。
- **复用 QuestionDeliveryService**：交互输入的答复路由复用现有 submitReply() 路径，不引入新的答复通道。

## Alternatives Considered

- **复用长期 InteractiveSession**：需要扩展 InteractiveSession 接口增加 reset() 方法，违反已归档的 interactive-session-interface spec，scope 膨胀。否决。
- **DefaultLoopDriver 直接管理 Lealone 连接**：破坏层级隔离，使 loop 层直接依赖 Lealone JDBC。否决。
- **绕过 Question/Answer 生命周期直接修改 LoopDriver 内部状态**：M29 scope 明确排除此方案，且会导致审计和权限语义分裂。否决。
- **交互输入直接触发 resume()**：交互模式的职责是提供人工处理入口，不应自动决定何时恢复循环。用户显式 close session 或外部调用 resume() 触发恢复。否决。
