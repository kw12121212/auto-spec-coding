# Design: agent-conversation

## Approach

1. 定义 `Message` sealed interface，携带 role、content、timestamp，使用 Java record 实现四个具体子类型
2. 定义 `Conversation` 类作为消息列表的可变容器，提供 append/get/history/clear 操作
3. 在 `AgentContext` 接口中新增 `conversation()` 方法，返回当前会话
4. 提供 `SimpleAgentContext` 作为默认实现，组合 sessionId、config、toolRegistry、conversation

## Key Decisions

- **Message 使用 sealed interface + record**：类型安全、模式匹配友好，与 Java 21+ 语言特性一致
- **Conversation 是可变容器而非 record**：会话需要频繁追加消息，不可变设计会引入不必要的对象创建开销
- **扩展 AgentContext 而非替换**：新增 `conversation()` 方法，保持现有签名不变，通过 default 方法提供向后兼容
- **timestamp 使用 long（epoch millis）**：与 Event 系统保持一致，避免引入 java.time 依赖

## Alternatives Considered

- **纯不可变 Conversation（每次返回新实例）**：放弃，因为编排循环中高频追加消息会导致大量对象分配
- **Message 只用一个类 + role 字段**：放弃，sealed interface 在编译期保证类型安全，便于后续 orchestrator 做模式匹配
- **独立的 ConversationManager 服务**：过度设计，当前阶段只需要简单的会话容器
