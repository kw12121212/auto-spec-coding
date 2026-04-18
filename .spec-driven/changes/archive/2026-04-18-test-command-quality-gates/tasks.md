# Tasks: test-command-quality-gates

## Implementation

- [x] 在 pom.xml 中引入 maven-checkstyle-plugin（bind 到 validate phase，使用 `google_checks.xml`，初始配置 `violationSeverity=warning`，`failsOnError=false`）
- [x] 在 surefire 默认配置中添加 `<excludedGroups>integration,e2e</excludedGroups>`，使 `mvnd test` 仅运行单元测试
- [x] 在 pom.xml 中新增 Maven profile `integration`，配置 `<groups>integration</groups>` 并取消 excludedGroups，用于运行集成测试
- [x] 为 `SessionStoreIntegrationTest.java` 添加 `@Tag("integration")`
- [x] 为 `ConfigLoaderVaultIntegrationTest.java` 添加 `@Tag("integration")`
- [x] 为 `BackgroundToolIntegrationTest.java` 添加 `@Tag("integration")`
- [x] 为 `RealSkillsIntegrationTest.java` 添加 `@Tag("integration")`
- [x] 为 `CrossLayerConsistencyTest.java` 添加 `@Tag("integration")`
- [x] 新增 delta spec 文件 `changes/test-command-quality-gates/specs/testing/test-quality-gates.md`，记录三层测试约定和标准命令（已完成于提案阶段）

## Testing

- [x] Run lint/validation — 确认 checkstyle 正常运行：`mvnd validate -DskipBuiltinToolsDownload`
- [x] Run unit tests — 确认集成测试未被执行：`mvnd test -DskipBuiltinToolsDownload`
- [x] Run integration tests — 确认集成测试被执行：`mvnd test -Pintegration -DskipBuiltinToolsDownload`
- [x] 确认单元测试命令下所有之前通过的单元测试仍然通过（无回归）
- [x] 确认集成测试命令下所有之前通过的集成测试仍然通过（无回归）

## Verification

- [x] 确认 `mvnd validate -DskipBuiltinToolsDownload` 输出 checkstyle 报告（即使有警告也不阻塞构建）
- [x] 确认 `mvnd test -DskipBuiltinToolsDownload` 不包含 `*IntegrationTest` 类的执行日志
- [x] 确认 `mvnd test -Pintegration -DskipBuiltinToolsDownload` 包含集成测试类的执行日志
- [x] 确认 delta spec `testing/test-quality-gates.md` 已记录所有四项需求
- [x] 确认实现与 proposal.md 和 design.md 一致，无超出范围的变更
