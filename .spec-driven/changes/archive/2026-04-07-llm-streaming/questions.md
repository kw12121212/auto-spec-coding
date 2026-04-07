# Questions: llm-streaming

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `TokenCounter` use character-based estimation (`text.length() / 4`) for v1, or integrate a Java tokenizer library (e.g., jtokkit) for accurate counts?
  Context: Character-based is simpler and dependency-free but inaccurate for non-English text and code. Accurate counting affects context window overflow detection reliability.
  A: 引入 jtokkit 库做精确计算。字符估算对中文/代码误差大，低估会导致 API 被拒（context_length_exceeded），不值得冒这个风险。jtokkit 轻量（~50KB）无外部依赖。

- [x] Q: Should `ContextWindowManager` be thread-safe, or is single-threaded per conversation sufficient?
  Context: If the agent orchestrator processes one LLM call at a time per conversation, thread-safety is unnecessary. If future parallel tool calls could trigger concurrent streaming, it would be needed.
  A: v1 不做线程安全，保持简单。当前编排循环是单线程顺序执行，且 ContextWindowManager 是 per-conversation 实例。需要时再加 AtomicLong 即可，接口不变。
