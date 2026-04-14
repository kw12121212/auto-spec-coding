# Design: interactive-session-interface

## Approach

本 change 只做 contract-first 设计，不直接落地 Lealone 适配器，也不修改 `DefaultLoopDriver` 的运行时流程。实现重心是新增一个最小但足够稳定的 `org.specdriven.agent.interactive` 抽象层，供后续 change 在同一个接口边界上迭代。

建议的最小公共面：
- `sessionId()`：暴露稳定的交互会话标识
- `state()`：暴露当前会话状态
- `start()`：显式启动会话
- `submit(String input)`：提交一条交互输入
- `drainOutput()`：按顺序提取当前待消费输出并清空已读缓冲
- `close()`：释放会话并进入终态

这样做可以让后续 adapter 把 Lealone 的 SQL/NL 对话能力映射为统一会话语义，同时让 loop bridge 和命令解析只依赖本项目自己的契约，而不是直接依赖某个底层 client 的细节。

## Key Decisions

- **先定义接口，不先实现桥接**：M29 还有 adapter、bridge、parser、SHOW/audit 多个 change。先锁定接口可以避免后续实现各自长出不同的会话语义。
- **使用独立 `interactive` 领域而不是塞进 `loop` 或 `question` 包**：交互式会话不是 loop 专属，也不是 question store 的子概念；单独领域更能表达它是未来 Lealone agent interaction 的统一边界。
- **输入与输出分离**：采用 `submit()` + `drainOutput()`，而不是一次输入立即返回单个响应，避免把未来实现限制成同步 request-response 模式。
- **输出使用有序 drain 语义**：后续 adapter 和 bridge 只需保证输出顺序与可重复消费边界，不必在首期就定义复杂的事件流模型。
- **不在本 change 引入具体命令语义**：SHOW 命令和自然语言解析属于后续 change，当前只定义会话容器和生命周期，不提前承诺命令集合。

## Alternatives Considered

- **直接定义 `LealoneAgentAdapter` 并让它成为事实标准**：被否决。这样会让后续 bridge 与 parser 直接依赖 Lealone 具体实现，失去 contract-first 的价值。
- **把交互方法直接加到 `DefaultLoopDriver`**：被否决。LoopDriver 负责调度和暂停恢复，不应直接承担通用交互会话接口职责。
- **采用回调/流式监听器作为首期契约**：被否决。首期只需要一个最小可测的同步接口边界，事件流或订阅模型可以由后续 change 在不破坏核心契约的前提下扩展。
- **在本 change 一并定义 SHOW 命令和答复回写协议**：被否决。这会把 `interactive-command-parser` 和 `interactive-show-audit` 的范围提前并入，超出当前计划项。
