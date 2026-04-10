# Design: loop-driver-core

## Approach

采用与现有代码库一致的模式：

1. **状态机** — 新增 `LoopState` 枚举，定义合法转换，`IllegalStateException` 拒绝非法转换。与 `AgentState` 模式一致。
2. **调度器** — `LoopDriver` 接口定义控制面，`DefaultLoopDriver` 实现调度逻辑。调度器直接读取 roadmap 文件系统（INDEX.md + milestone .md），不依赖数据库。
3. **配置** — `LoopConfig` record 持有安全限制参数，提供 `defaults()` 静态工厂。
4. **事件** — 在 `EventType` 中新增 `LOOP_STARTED`、`LOOP_PAUSED`、`LOOP_RESUMED`、`LOOP_STOPPED`、`LOOP_ITERATION_COMPLETED`、`LOOP_ERROR`，通过 `EventBus` 发布。
5. **迭代追踪** — `LoopIteration` record 记录每轮的 change name、开始时间、结束时间、结果状态。`LoopDriver` 内部维护迭代列表和计数器。
6. **线程安全** — 调度器运行在独立 VirtualThread 上，状态变更通过 `synchronized` 保护，事件发布到 `EventBus`。

## Key Decisions

- **调度器读取文件系统而非数据库** — roadmap/milestone 数据以 markdown 文件形式存在，直接解析文件比引入额外的数据库表更简单、与现有 spec-driven CLI 工具一致。
- **LoopDriver 是独立接口，不扩展 Agent** — 循环调度器的生命周期与单个 Agent 不同（一个循环可能跨越多个 Agent session），保持接口独立。
- **安全限制在配置层而非硬编码** — `LoopConfig` 支持最大循环次数、单次超时等参数，默认值保守（如最大 10 次循环），避免资源耗尽。
- **新增 EventType 而非复用现有值** — 循环有独特的状态语义（如 `LOOP_ITERATION_COMPLETED`），不复用 `AGENT_STATE_CHANGED` 以保持事件语义清晰。

## Alternatives Considered

- **将循环状态存入 Lealone DB** — 可行但增加复杂度。首期用内存 + 事件发布即可，持久化留给 `loop-progress-persistence` change。
- **让 LoopDriver 扩展 Agent 接口** — 循环跨越多个 Agent session，状态模型不匹配。独立接口更清晰。
- **在调度器内部调用 recommend/auto skill** — 这属于 `loop-recommend-auto-pipeline` 的职责。本 change 只提供调度框架和状态机。
