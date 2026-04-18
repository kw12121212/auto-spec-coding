# Design: test-fixture-standardization

## Approach

### 1. 共享 `CapturingEventBus`

在 `org.specdriven.agent.testsupport` 包（已存在）中新建 `CapturingEventBus.java`，实现 `EventBus` 接口，将 `publish` 的事件追加到内部 `List<Event>`，暴露 `getEvents()` 和 `clear()` 方法。

各测试文件中的私有内部类逐一替换为对共享实现的引用，删除本地副本。

### 2. `LealoneTestDb` 工具类

在 `testsupport` 包中新建 `LealoneTestDb.java`，提供：

```java
public static String freshJdbcUrl() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return "jdbc:lealone:embed:test_db_" + suffix + "?PERSISTENT=false";
}
```

覆盖全部 31 个调用点，统一替换为 `LealoneTestDb.freshJdbcUrl()`。

### 3. `HttpTestStack` 可见性确认

当前 `HttpTestStack` 为 `http` 包内的包私有类（`final class`）。由于它强依赖 `AuthFilter`、`RateLimitFilter`、`HttpApiServlet`，保留在 `http` 包下合理，不移入 `testsupport`。仅核实构造函数可见性与调用方一致。

## Key Decisions

- **保留 `HttpTestStack` 在 `http` 包**：它的依赖全部在 `http` 包内，跨包搬迁会引入不必要的可见性暴露。
- **不提取领域特定桩（StubLlmProvider 等）**：`StubProvider`/`StubLlmClient` 在不同测试中行为差异较大，跨域共享会掩盖行为语义。
- **`CapturingEventBus` 使用线程安全的 `CopyOnWriteArrayList`**：多个测试涉及并发事件发布，防御性使用线程安全集合避免引入新 flaky。

## Alternatives Considered

- **JUnit 5 Extension**：可通过 `@ExtendWith` 注入夹具，但会引入注解魔法，增加认知负担，对当前规模不值得。
- **抽象基类 `AbstractLealoneStoreTest`**：共享 `setUp` 逻辑，但继承导致测试文件之间的隐式耦合，违背"每个测试独立"约定。静态工具方法更清晰。
