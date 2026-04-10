# Questions: loop-progress-persistence

## Open

<!-- No open questions -->

## Resolved

- [x] Q: 循环进度持久化的存储后端选择？
  Context: 决定接口设计风格、恢复逻辑复杂度和与 M26 对接方式
  A: 采用 Lealone 嵌入式 DB，与项目已有 Store 实现保持架构一致
