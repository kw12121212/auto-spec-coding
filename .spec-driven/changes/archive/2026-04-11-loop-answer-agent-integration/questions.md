# Questions: loop-answer-agent-integration

<!-- No open questions -->

## Resolved

- [x] Q: 从循环内部调用 M22 Answer Agent 的接口契约是什么（同步 vs 异步、超时行为）？
  Context: 决定 LoopAnswerAgent 是否阻塞循环线程，以及超时如何触发升级。
  A: 同步阻塞调用 + 可配置超时，超时阈值复用 `LoopConfig.iterationTimeoutSeconds()`。超时视为升级触发条件，由后续 `loop-escalation-gate` 变更处理。（用户于 2026-04-11 确认）
