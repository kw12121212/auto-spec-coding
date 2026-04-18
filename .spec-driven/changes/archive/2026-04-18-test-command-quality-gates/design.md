# Design: test-command-quality-gates

## Approach

**Lint/Validation 入口**
在 pom.xml 中绑定 maven-checkstyle-plugin 到 `validate` phase，使用 Google Java Style 规则集（`google_checks.xml`，来自 checkstyle 官方分发）。这样 `mvnd validate -DskipBuiltinToolsDownload` 即为 lint 标准命令；`mvnd test -DskipBuiltinToolsDownload` 自动先经过 validate 再运行单元测试。

**测试分层与 tag 约定**
JUnit 5 原生支持 `@Tag`，无需额外依赖。约定：
- `unit`：快速、无 I/O、不依赖数据库或外部进程的测试（默认不标注 tag，surefire 默认执行）
- `integration`：依赖嵌入式 Lealone DB、文件系统、子进程或跨层协作的测试（标注 `@Tag("integration")`）
- `e2e`：通过 HTTP/JSON-RPC 端点驱动的端到端测试（标注 `@Tag("e2e")`，暂未有此类测试，预留约定）

**surefire 配置**
- 默认 surefire 配置新增 `<excludedGroups>integration,e2e</excludedGroups>`，单元测试命令干净快速
- 新增 Maven profile `integration`，在该 profile 下 `<groups>integration</groups>` 并取消 excludedGroups，运行集成测试
- 集成测试命令：`mvnd test -Pintegration -DskipBuiltinToolsDownload`

## Key Decisions

1. **选择 surefire + `@Tag` 而非 maven-failsafe-plugin**：failsafe 需要独立的 `integration-test` 和 `verify` phase，适合需要预/后置容器生命周期的场景。本项目集成测试使用嵌入式 Lealone 且不需要外部容器，surefire + profile 更轻量且与现有构建一致。

2. **选择 Google Java Style 规则集而非自定义规则集**：项目 CLAUDE.md 引用了"standard Java conventions (checkstyle)"但没有自定义规则文件。使用官方 `google_checks.xml` 是最小摩擦选择；若未来需要调整，可在 pom.xml 中覆盖特定规则。

3. **不强制现有代码零违规**：首次引入 checkstyle 时，若现有代码有大量风格违规，将 `<failsOnError>false</failsOnError>` 和 `<violationSeverity>warning</violationSeverity>` 作为初始配置，避免 lint 引入阻塞现有开发流程。此为过渡策略，后续 change 可收紧。

4. **保留 `mvnd` 而非 `mvn`**：M40 里程碑明确约定本里程碑内所有验证命令使用 `mvnd`。

## Alternatives Considered

- **maven-failsafe-plugin 运行集成测试**：需要额外 phase 绑定，且本项目集成测试不依赖外部服务器启动/停止生命周期，引入 failsafe 增加复杂度而无实质收益。
- **自定义 checkstyle 规则文件**：规则文件需要维护，且目前没有已知的自定义风格需求。Google 规则集已被广泛认可，足以覆盖常见代码风格检查。
- **使用 SpotBugs/PMD 替代 checkstyle**：这些工具检查语义缺陷，不是风格/lint，与"lint 命令入口"的目标不同；可在后续 change 中补充，不属于本 change 范围。
