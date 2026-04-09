# Questions: skill-auto-discovery

## Open

## Resolved

- [x] Q: 集成测试（RealSkillsDiscoveryTest）执行 CREATE SERVICE DDL 时需要真实的 Lealone 嵌入式实例——测试的 jdbcUrl 应该用哪个数据库名（如 `jdbc:lealone:embed:test_discovery_db`），还是复用现有测试数据库？
  Context: 执行 DDL 会在数据库中持久注册 service，需确认测试环境是否使用独立的临时数据库以避免污染。
  A: 使用 `jdbc:lealone:embed:test_skill_discovery?PERSISTENT=false`（与现有测试模式一致）。若 CREATE SERVICE 在该模式下不支持，T6 只测试扫描/解析/错误路径（跳过 DDL 执行验证），T7 集成测试改为条件跳过。

