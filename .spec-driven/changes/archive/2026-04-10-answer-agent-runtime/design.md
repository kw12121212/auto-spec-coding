# Design: answer-agent-runtime

## Approach

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    DefaultOrchestrator                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  handleQuestionToolCall()                               │   │
│  │    ├── deliveryMode == AUTO_AI_REPLY ──────┐            │   │
│  │    │                                        ▼            │   │
│  │    │  ┌──────────────────────────────────────────┐      │   │
│  │    └──►│     AnswerAgentRuntime.resolve()        │      │   │
│  │       │  ┌────────────────────────────────────┐  │      │   │
│  │       │  │ ContextWindowManager.crop()        │  │      │   │
│  │       │  │  - 提取问题相关上下文              │  │      │   │
│  │       │  │  - 移除敏感/无关信息               │  │      │   │
│  │       │  └────────────────────────────────────┘  │      │   │
│  │       │  ┌────────────────────────────────────┐  │      │   │
│  │       │  │ AnswerGenerationService.generate() │  │      │   │
│  │       │  │  - 调用 LLM                        │  │      │   │
│  │       │  │  - 解析响应                        │  │      │   │
│  │       │  │  - 构建结构化 Answer               │  │      │   │
│  │       │  └────────────────────────────────────┘  │      │   │
│  │       └──────────────────────────────────────────┘      │   │
│  │                         │                               │   │
│  │                         ▼                               │   │
│  │       ┌────────────────────────────────────┐           │   │
│  │       │  直接返回 Answer（不进入 PAUSED）   │           │   │
│  │       └────────────────────────────────────┘           │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 关键设计决策

#### 1. Answer Agent 不进入 PAUSED 状态

与人工等待模式不同，Answer Agent 处理是同步的、自动化的：
- 主 Agent 调用 `AnswerAgentRuntime.resolve(question, conversation)`
- Answer Agent 在后台运行，生成答复
- 直接返回 `Answer`，主 Agent 继续执行，不经过 `PAUSED` 状态

**原因**：
- AUTO_AI_REPLY 的目标是无需人工介入，同步处理更符合预期
- 避免不必要的状态转换和事件发射
- 简化错误处理（失败可降级为抛出异常或转为人工模式）

#### 2. 上下文裁剪策略

Answer Agent 不应接收完整的对话历史，而应获得最小必要上下文：

```java
// ContextWindowManager 裁剪规则
List<Message> crop(List<Message> fullHistory, Question question) {
    // 1. 保留最近的 N 条消息（可配置，默认 10）
    // 2. 保留所有 system 消息
    // 3. 保留与 question 相关的 tool 调用结果
    // 4. 移除可能包含敏感信息的早期消息
}
```

**原因**：
- 减少 LLM token 消耗
- 避免主 Agent 的错误假设被 Answer Agent 继承
- 符合 M22 规范："Answer Agent 应使用裁剪后的最小必要上下文"

#### 3. Answer Agent 配置独立

Answer Agent 使用独立的 LLM 配置，与主 Agent 解耦：

```java
public record AnswerAgentConfig(
    String provider,      // 如 "openai", "claude"
    String model,         // 如 "gpt-4o-mini"（轻量级模型即可）
    double temperature,   // 默认 0.3（更低，更确定性）
    int maxTokens,        // 默认 1024
    long timeoutSeconds,  // 默认 30
    int maxContextMessages  // 默认 10
) {}
```

**原因**：
- Answer Agent 任务相对简单（分析单个问题），可用更轻量级模型
- 独立的超时控制，避免阻塞主 Agent 过久
- 独立的温度参数，Answer Agent 需要更确定性的答复

#### 4. 错误处理策略

| 错误场景 | 处理策略 |
|---------|---------|
| LLM 调用超时 | 抛出 `AnswerAgentTimeoutException`，主 Agent 可选择重试或转为人工模式 |
| LLM 返回无效格式 | 抛出 `AnswerAgentException`，包含原始响应用于调试 |
| 上下文裁剪后为空 | 使用原始 system prompt + question payload 作为 fallback |
| Answer 验证失败 | 记录警告，尝试修复（如截断过长内容）或抛出异常 |

## Key Decisions

### 决策 1：是否复用 DefaultAgent 作为 Answer Agent？

**选项 A**：复用 `DefaultAgent`
- 优点：代码复用，生命周期管理一致
- 缺点：`DefaultAgent` 设计用于完整 Agent 运行，过于重量级；Answer Agent 只需要单次 LLM 调用

**选项 B**：独立轻量级实现（选择）
- 优点：更轻量，配置独立，职责清晰
- 缺点：少量代码重复

**结论**：选择 B。Answer Agent 的职责是单一问题答复，不需要完整的 Agent 生命周期。

### 决策 2：Answer Agent 是否支持工具调用？

**选项 A**：支持工具调用
- 优点：Answer Agent 可以查询额外信息来生成更准确的答复
- 缺点：增加复杂度，可能引入无限递归（Answer Agent 又产生 question）

**选项 B**：不支持工具调用（选择）
- 优点：简单、安全、可预测
- 缺点：Answer Agent 只能基于已有上下文答复

**结论**：选择 B。首期保持简单，仅基于已有上下文生成答复。如需查询额外信息，应由主 Agent 在提问前完成。

### 决策 3：Answer Agent 的答复如何验证？

**选项 A**：LLM 自我评估置信度
- 优点：可能更准确
- 缺点：增加一次 LLM 调用，延迟增加

**选项 B**：基于启发式规则（选择）
- 优点：快速、确定性
- 缺点：可能不够精确

**结论**：选择 B。使用简单规则：
- `confidence = 0.9`（Answer Agent 默认高置信度）
- `decision = ANSWER_ACCEPTED`
- `source = AI_AGENT`

如需更复杂的置信度评估，可在后续迭代中扩展。

## Alternatives Considered

### 替代方案 1：异步 Answer Agent

Answer Agent 异步运行，主 Agent 进入 PAUSED 状态，等待 Answer Agent 完成。

**放弃原因**：
- 增加不必要的复杂度（需要状态管理、事件通知）
- Answer Agent 处理通常很快（< 5 秒），同步等待可接受
- 与人工等待模式混淆，增加理解成本

### 替代方案 2：预编译 Answer Agent 答复模板

对常见 question 类型预编译答复模板，Answer Agent 直接匹配返回。

**放弃原因**：
- 与项目目标不符（需要通用 Agent 能力，非特定领域）
- 模板维护成本高
- LLM 调用成本已足够低，模板优化收益有限

### 替代方案 3：Answer Agent 作为独立进程/服务

Answer Agent 运行在独立进程或远程服务中，通过 IPC/HTTP 通信。

**放弃原因**：
- 过度设计，Answer Agent 是轻量级组件
- 增加部署复杂度
- 与现有架构（Lealone 嵌入式）不一致
