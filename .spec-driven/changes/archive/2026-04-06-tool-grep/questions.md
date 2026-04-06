# Questions: tool-grep

## Open

<!-- No open questions -->

## Resolved

- [x] Q: tool-grep 优先使用纯 Java NIO 实现，还是预留 ripgrep 二进制调用接口？
  Context: M2 scope vs M3 builtin-tool-manager 的边界划分
  A: 纯 Java NIO 实现，ripgrep 集成留给 M3

- [x] Q: 输出模式是否需要支持多种格式？
  Context: 与 Go 实现功能对齐
  A: 支持 content、files_with_matches、count 三种模式
