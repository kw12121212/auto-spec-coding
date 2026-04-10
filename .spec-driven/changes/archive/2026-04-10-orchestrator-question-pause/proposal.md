# orchestrator-question-pause

## What

为 M22 增加 question-handling 的运行时暂停能力：当主 agent 在编排循环中遇到需要延后答复的问题时，系统可创建结构化 `Question`、进入等待答复状态、在收到匹配 `Answer` 后恢复同一会话继续执行，并在超时后结束当前等待。

## Why

`question-contract-audit` 已经定义了 `Question` / `Answer` 契约、生命周期状态和审计事件，但当前运行时仍只支持普通 LLM/tool 循环，[DefaultOrchestrator](\/home\/code\/Code\/auto-spec-coding\/src\/main\/java\/org\/specdriven\/agent\/agent\/DefaultOrchestrator.java) 还没有暂停、等待、恢复或超时路径。

如果不先补齐这层运行时能力，后续的 `answer-agent-runtime`、`question-routing-policy`、`question-delivery-surface` 都只能各自发明等待与恢复机制，既难以复用，也会让 M23 的移动交互集成缺少稳定基础。

## Scope

- 修改 `agent-interface` 与 `question-resolution` 主规格对应的 delta spec，定义 question 触发的暂停、等待、答复恢复、超时与单会话约束
- 规定 orchestrator 在等待人工答复问题时的可观察行为：创建问题、发出事件、暂停执行、停止继续调用 LLM / Tool、接收答复后恢复同一会话
- 规定等待超时后的可观察行为：问题过期、拒绝晚到答复、当前运行不再继续推进
- 保持 question payload、answer 审计字段与已完成的 `question-contract-audit` 契约一致，不重复定义字段结构
- 不在此变更中实现 `AUTO_AI_REPLY` 的 answer agent 自动答复链路；该部分留给 `answer-agent-runtime`
- 不在此变更中实现移动推送、回调验签、渠道模板或渠道观测；这些留给 `question-delivery-surface` 与 M23
- 不在此变更中新增 Native Java SDK / HTTP / JSON-RPC 的 pending-question 查询或人工答复公开 API

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- 不涉及 question 的普通 agent 运行路径保持不变：LLM 文本回复、工具调用顺序和错误回填行为不变
- `Question` / `Answer` 的字段契约、事件命名和审计字段保持不变
- `SdkAgent.run()` 的现有公开方法集合保持不变，不在本变更中新增用户可见入口
