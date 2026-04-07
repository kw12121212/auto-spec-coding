# Questions: agent-session-store

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Session 数据的序列化格式应使用 JSON 字符串还是二进制格式？
  Context: Lealone 无原生 JSON 列类型（已通过 jar 包验证），需选择在 VARCHAR/CLOB 中存储的格式。
  A: JSON 字符串，核心字段（id、state、timestamps）建结构化列，messages 以 JSON CLOB 存储，使用 Lealone ORM JsonObject 序列化。

- [x] Q: Session ID 由 SDK 生成还是由外部传入？
  Context: 影响 SessionStore.save 的签名和调用方如何持有引用。
  A: SDK 自动生成 UUID；save 返回 ID 供调用方持有，支持外部按 ID 查询。

- [x] Q: 旧 Session 的保留策略？
  Context: 无限增长会导致 DB 膨胀，需明确清理规则。
  A: TTL 30 天自动清理，从 Session 创建时起算，不随 save 重置。
