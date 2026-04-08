# Questions: sdk-error-handling

## Open

<!-- No open questions -->

## Resolved

- [x] Q: 错误层次结构采用子类继承还是扁平 + ErrorCode？
  Context: 决定 SDK 异常的 API 风格，影响调用方错误处理方式
  A: 子类继承 — Java 惯用，IDE 友好

- [x] Q: 是否携带 retryable 标记？
  Context: M13/M14 自动重试机制需要区分可重试错误
  A: 是 — 低实现成本，直接服务于下游里程碑

- [x] Q: 范围仅限新类型还是同时改造现有代码？
  Context: 仅加类型不集成则无实际产出
  A: 同时改造 — 否则 M12 错误处理目标形同虚设
