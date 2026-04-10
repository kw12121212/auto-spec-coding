# Questions: loop-driver-core

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `LoopScheduler` read milestone files from disk on every `selectNext()` call, or should it cache the parsed roadmap state at loop start and refresh only on checkpoint?
  Context: Disk I/O frequency affects performance and consistency.
  A: 每次 `selectNext()` 从磁盘读取。文件小（几 KB）解析开销可忽略，保证读到最新 roadmap 状态，与后续 checkpoint 恢复更自然配合。

- [x] Q: Should `DefaultLoopDriver.start()` accept an optional list of target milestone files to restrict the loop scope, or always scan all milestones?
  Context: Restricting scope gives operators control but adds API surface.
  A: 不在 `start()` 加参数，通过 `LoopConfig` 增加可选字段 `targetMilestones` (List<String>)。为空时扫描全部，非空时只跑指定的 milestone。
