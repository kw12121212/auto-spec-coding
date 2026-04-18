# Tasks: test-fixture-standardization

## Implementation

- [x] 在 `org.specdriven.agent.testsupport` 包中新建 `CapturingEventBus.java`，实现 `EventBus` 接口，使用 `CopyOnWriteArrayList` 捕获事件，暴露 `getEvents()` 和 `clear()` 方法
- [x] 在 `org.specdriven.agent.testsupport` 包中新建 `LealoneTestDb.java`，提供 `freshJdbcUrl()` 静态工具方法
- [x] 将以下测试文件中的私有 `CapturingEventBus` 内部类替换为对共享实现的引用：`LealoneTaskStoreTest`、`LealoneTeamStoreTest`、`LealoneCronStoreTest`、`LealoneVaultTest`、`VaultFactoryTest`、`LealoneLoopIterationStoreTest`、`InteractiveCommandHandlerTest`、`RetryingDeliveryChannelTest`、`LealoneLlmCacheTest`（确认并处理所有副本）
- [x] 将 31 个测试文件中手写的 Lealone 内存 JDBC URL 构造替换为 `LealoneTestDb.freshJdbcUrl()`
- [x] 核实 `HttpTestStack` 在 `http` 包内的包私有可见性与调用方一致，必要时调整（保留在 `http` 包，不迁移）

## Testing

- [x] 运行 lint 验证：`mvnd checkstyle:check`（失败项均为预存的 SkillMarkdownParser.java 问题，与本变更无关）
- [x] 运行 unit tests：`mvnd test`（1901 个测试全部通过，零失败）

## Verification

- [x] `testsupport` 包中存在 `CapturingEventBus.java` 和 `LealoneTestDb.java`，无私有副本残留
- [x] 所有 `jdbc:lealone:embed:` 字符串直接构造已被替换为 `LealoneTestDb.freshJdbcUrl()` 调用
- [x] 实现与 proposal.md 和 design.md 描述一致，无超出范围的改动
