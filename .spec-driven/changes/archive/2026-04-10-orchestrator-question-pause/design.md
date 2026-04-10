# Design: orchestrator-question-pause

## Approach

1. 在 orchestrator 运行时引入通用的“等待问题答复”阶段，而不是直接把答复逻辑绑到 SDK、HTTP 或移动渠道上。这样后续不同入口都可以复用同一套暂停/恢复机制。
2. 当主 agent 产生一个需要延后答复的结构化问题时，运行时先创建 `Question` 并发布 `QUESTION_CREATED`，再把 agent 状态从 `RUNNING` 切换到 `PAUSED`，随后阻断新的 LLM 调用与工具执行，直到收到匹配答复或超时。
3. 恢复路径只接受与当前等待问题匹配的答复；答复被接受后，运行时更新问题状态、发布 `QUESTION_ANSWERED`、把答复写入会话历史，再切回 `RUNNING` 并继续原有编排循环。
4. 超时路径把问题标记为 `EXPIRED` 并发布 `QUESTION_EXPIRED`。当前等待结束后，本次运行不再继续推进额外的 LLM/tool turn；后续是否升级、重试或换路由由其他 change 决定。

## Key Decisions

- 本 change 只覆盖 `PAUSE_WAIT_HUMAN` 与 `PUSH_MOBILE_WAIT_HUMAN` 两类“需要等待外部答复”的运行时语义，不把 `AUTO_AI_REPLY` 自动答复链路塞进来，避免与 `answer-agent-runtime` 重叠。
- 每个 session 首期只允许一个未决 waiting question。由于 orchestrator 在问题点会整体暂停，多问题并发等待不会提升能力，反而会显著增加恢复与审计复杂度。
- 答复恢复只要求通用的 question/answer 提交语义，不直接承诺具体 SDK、HTTP 或 JSON-RPC API 形态；这样可以把 transport surface 留给 `question-delivery-surface` 再统一定义。
- 超时只负责“结束等待并过期问题”，不在本变更里自动升级到人工或自动改走移动端，以免提前固化 `question-routing-policy` 的决策规则。
- 恢复前写入会话历史的是规范化后的答复内容与审计上下文，而不是某个渠道的原始 payload，避免后续 transport/channel 细节泄漏到核心会话模型。

## Alternatives Considered

- 在本 change 中同时加入 answer agent 自动答复：放弃。这样会把 pause/resume 运行时与 AI 代答策略耦合，难以单独验证暂停语义。
- 先做 `question-delivery-surface` 再补 runtime：放弃。没有稳定的等待/恢复基础，任何公开 API 都会过早绑定不成熟的运行时行为。
- 允许同一 session 并行存在多个 waiting question：放弃。当前路线图没有该复杂度的明确需求，而且会直接放大恢复顺序、超时冲突和审计解释成本。
