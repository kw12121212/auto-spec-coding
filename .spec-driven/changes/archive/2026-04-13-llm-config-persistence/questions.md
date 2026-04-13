# Questions: llm-config-persistence

## Open

<!-- No open questions -->

## Resolved

### Question: 这次变更是否只持久化默认的运行时 LLM 配置，还是连每个 session 的覆盖配置也一起持久化？

**Decision**: 只持久化默认运行时 LLM 配置；session 级覆盖保持为仅运行期有效。

**Rationale**: 这样可以先把 M28 的持久化主链路做稳，避免在同一个 proposal 中提前定义 session 配置的生命周期、恢复规则和清理策略，保持 scope 与 roadmap 的最小可行增量一致。（用户已确认接受该建议）

---

### Question: “版本记录与回滚能力”这次是只做到历史版本与内部恢复，还是同时提供用户可直接使用的回滚入口？

**Decision**: 本次只定义历史版本与内部恢复能力，不提供新的用户可见回滚入口。

**Rationale**: 这能让本 change 聚焦于持久化骨架，避免过早扩展到 SQL/API surface、权限治理和审计设计。用户可见的回滚入口留给后续 `set-llm-sql-handler` 或治理相关 change 再定义更合适。（用户已确认接受该建议）
