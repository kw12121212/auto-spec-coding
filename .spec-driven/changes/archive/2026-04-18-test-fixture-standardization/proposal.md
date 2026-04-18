# test-fixture-standardization

## What

将仓库中多处重复出现的测试辅助代码提取并标准化为 `org.specdriven.agent.testsupport` 包下的共享夹具，主要目标：

1. 将分散在 8+ 个测试文件中各自私有的 `CapturingEventBus` 内部类合并为 `testsupport` 包下唯一的共享实现
2. 将 46 处（31 个文件）重复的 Lealone 内存 JDBC URL 构造逻辑封装为 `LealoneTestDb.freshJdbcUrl()` 静态工具方法
3. 确认 `HttpTestStack`、`SubprocessTestCommand`、`JsonRpcStdio` 等已有夹具的访问修饰符与使用边界合理，必要时做可见性调整

## Why

M40 里程碑要求降低新增测试的重复样板成本并使现有测试基础设施可被后续变更直接复用。当前状态：

- `CapturingEventBus`（EventBus 事件捕获用桩）作为私有内部类在 `LealoneTaskStoreTest`、`LealoneTeamStoreTest`、`LealoneCronStoreTest`、`LealoneVaultTest`、`VaultFactoryTest`、`LealoneLoopIterationStoreTest`、`InteractiveCommandHandlerTest`、`RetryingDeliveryChannelTest` 等处各自独立定义，实现略有差异却语义相同
- `"jdbc:lealone:embed:" + dbName + "?PERSISTENT=false"` 字符串拼接散落在 31 个测试文件中，每个文件各自生成随机 DB 名，不是 DRY 的

将这些模式集中到 `testsupport` 包后，新增同类测试只需一行调用，不再需要复制样板。

## Scope

**In scope:**
- `src/test/` 目录下的测试代码重构
- 在 `org.specdriven.agent.testsupport` 包中新增 `CapturingEventBus` 和 `LealoneTestDb` 共享辅助类
- 将现有测试文件中的私有 `CapturingEventBus` 内部类替换为对共享实现的引用
- 将现有测试文件中手写的 Lealone JDBC URL 构造替换为 `LealoneTestDb.freshJdbcUrl()`
- 核实 `HttpTestStack` 的包可见性和使用边界

**Out of scope:**
- 任何生产代码 (`src/main/`) 改动
- 提取 `StubLlmProvider`、`StubLlmClient` 等领域特定桩（各自耦合特定测试领域，不应跨域共享）
- 引入 JUnit extension 或测试框架层面的大规模重构
- 增加新的业务行为回归测试
- 纯覆盖率堆量

## Unchanged Behavior

- 所有现有测试的断言逻辑和覆盖范围保持不变
- 共享 `CapturingEventBus` 的捕获语义必须与各私有副本一致（捕获所有 publish 事件、可用 getEvents() 查询）
- 生产代码行为不受任何影响
