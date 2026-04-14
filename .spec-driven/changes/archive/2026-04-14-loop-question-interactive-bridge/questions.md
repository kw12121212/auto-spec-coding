# Questions: loop-question-interactive-bridge

## Open

<!-- No open questions -->

## Resolved

- [x] Q: 每次暂停点是创建新的 InteractiveSession 还是复用长期会话？
  Context: InteractiveSession 生命周期为 NEW → ACTIVE → CLOSED/ERROR，不支持重启。复用需扩展接口。
  A: 每次暂停创建新 session。符合现有生命周期设计，不修改已归档的 interactive-session-interface spec。

- [x] Q: 交互会话是否需要超时机制？
  Context: 长时间交互可能导致后台资源（数据库连接、HTTP 会话等）超时释放。
  A: 首期不设超时。交互模式仅面向本地操作者，资源风险可控。超时行为可后续以最小变更添加。
