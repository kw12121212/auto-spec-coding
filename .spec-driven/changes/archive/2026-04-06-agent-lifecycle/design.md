# Design: agent-lifecycle

## Approach

在 `org.specdriven.agent.agent` 包下新增 `DefaultAgent` 类，实现 `Agent` 接口。内部使用 `volatile AgentState` 字段跟踪当前状态，所有生命周期方法通过 `transitionTo(targetState)` 进行状态转换，该方法检查转换合法性。

状态转换规则：

```
IDLE    → RUNNING   (start)
RUNNING → PAUSED    (pause — 预留给 orchestrator)
RUNNING → STOPPED   (stop)
RUNNING → ERROR     (异常时自动转换)
PAUSED  → RUNNING   (resume)
PAUSED  → STOPPED   (stop)
ERROR   → STOPPED   (stop)
STOPPED → (terminal)
```

`init()` 在 IDLE 之前执行，完成初始化后将状态设为 IDLE。`close()` 可从任意状态调用，释放资源后将状态设为 STOPPED（如果还不是的话）。

`execute(AgentContext)` 仅在 RUNNING 状态可调用，执行过程中如发生未捕获异常则自动转换到 ERROR。

## Key Decisions

1. **不使用枚举 transition map** — 合法转换数量有限（约 7 条），直接用 switch/if 检查即可，无需引入 Transition 表。保持代码简单。
2. **volatile 而非锁** — Agent 状态机是单线程驱动的（VirtualThread 协程模型），volatile 足以保证可见性。如果后续并发需求增加，再升级为 AtomicReference。
3. **execute 异常自动转 ERROR** — 与 Lealone plugin 的 error 语义对齐，调用方无需手动处理状态转换。
4. **pause/resume 方法暂不加到 Agent 接口** — PAUSED 状态通过预留的转换路径支持，具体 pause/resume API 留给 `agent-orchestrator` change 在子类或扩展接口中定义。

## Alternatives Considered

1. **AtomicReference<AgentState>** — 过度设计，当前场景不需要 CAS 操作。
2. **状态模式（State pattern）** — 每个状态一个类，对于 5 个状态、7 条转换来说过于复杂。
3. **在 Agent 接口加 pause/resume** — 违反 YAGNI，pause/resume 的具体语义应由 orchestrator 定义。
