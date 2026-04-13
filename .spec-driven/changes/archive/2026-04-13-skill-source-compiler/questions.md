# Questions: skill-source-compiler

## Open

<!-- No open questions -->

## Resolved

- [x] Q: 是否将 Lealone `SourceCompiler` 相关的依赖升级或版本对齐纳入本 change 的主体范围？
  Context: 这会决定 `skill-source-compiler` 只定义项目侧编译契约，还是同时承担依赖升级与兼容性调整工作。
  A: 不纳入主体范围；本 change 聚焦项目侧的编译抽象与契约，只把当前 Lealone 依赖能力验证作为任务记录下来。若验证发现现有依赖不满足，再单独扩 scope 或拆后续 change。
