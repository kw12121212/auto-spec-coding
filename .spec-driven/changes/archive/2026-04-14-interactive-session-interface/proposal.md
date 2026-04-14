# interactive-session-interface

## What

为 M29 引入一个独立的 `InteractiveSession` 契约，先把交互式会话的最小生命周期和 I/O 边界定义清楚，再由后续 roadmap change 分别实现 Lealone 适配、LoopDriver 桥接、命令解析和 SHOW/audit 能力。

具体交付内容：
- 新增 `InteractiveSession` 接口规范，覆盖会话标识、启动、输入提交、输出读取、状态查询和关闭行为
- 新增 `InteractiveSessionState` 状态模型，明确 `NEW`、`ACTIVE`、`CLOSED` 和 `ERROR` 的可观察语义
- 规定输入必须在活动态提交、输出必须按顺序可读取且支持 drain 语义
- 为后续 `lealone-agent-adapter` 和 `loop-question-interactive-bridge` 提供稳定的抽象边界，避免后续 change 直接耦合 Lealone 交互细节
- 单元测试覆盖生命周期、非法状态调用、空输入拒绝和输出 drain 语义

## Why

M29 的其余 planned changes 都依赖一个先定义好的交互式会话契约：如果没有 `InteractiveSession` 这一层，后续实现很容易把 `LoopDriver`、`QuestionRuntime` 和 Lealone 的 SQL/NL 交互能力直接绑死在一起，导致桥接逻辑、命令解析和审计能力缺少统一边界。

从 dependency order 看，`interactive-session-interface` 是 M29 中最适合作为入口的 change：它不要求一次解决交互解析、SHOW 命令或 Lealone 绑定，但能为这些后续 change 提供统一生命周期协议，也让未来的 M32 平台化整合有一个稳定的 agent-interaction 能力接口可依赖。

## Scope

**In scope：**
- 定义 `InteractiveSession` 的最小公共契约
- 定义交互式会话状态及状态语义
- 定义输入提交、输出读取和关闭行为的可观察规则
- 规划实现/测试映射，落到独立的 `org.specdriven.agent.interactive` 领域路径
- 补充单元测试与验证任务

**Out of scope：**
- 实现 `LealoneAgentAdapter`；属于 `lealone-agent-adapter`
- 将 `DefaultLoopDriver` 真正接入交互式模式；属于 `loop-question-interactive-bridge`
- 自然语言命令解析、SHOW SERVICES / SHOW STATUS / SHOW ROADMAP；属于后续 M29 changes
- 新增 HTTP/JSON-RPC 远程交互入口
- 改变现有 Question/Answer、LoopDriver 暂停恢复或 QuestionDeliveryService 的运行时行为

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- 现有 `DefaultLoopDriver` 的 `QUESTIONING -> PAUSED` 升级路径保持不变
- 现有 `QuestionRuntime`、`QuestionDeliveryService` 和 pending-question 行为保持不变
- 本 change 不引入 Lealone SQL/NL 交互实现，也不改变现有 SDK/HTTP/JSON-RPC 对外接口
- M29 后续 change 仍分别负责 adapter、bridge、parser 和 audit 功能，不在本 change 中提前实现
