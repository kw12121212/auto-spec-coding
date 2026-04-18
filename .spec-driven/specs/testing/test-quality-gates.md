---
mapping:
  implementation:
    - pom.xml
  tests:
    - src/test/java/org/specdriven/agent/agent/SessionStoreIntegrationTest.java
    - src/test/java/org/specdriven/agent/config/ConfigLoaderVaultIntegrationTest.java
    - src/test/java/org/specdriven/agent/tool/BackgroundToolIntegrationTest.java
    - src/test/java/org/specdriven/skill/sql/RealSkillsIntegrationTest.java
    - src/test/java/org/specdriven/agent/integration/CrossLayerConsistencyTest.java
---

## Requirements

### Requirement: standard-lint-command
仓库 MUST 提供可执行的 lint/validation 命令作为标准代码风格验证入口。

#### Scenario: lint passes on valid code
- GIVEN 仓库代码符合 checkstyle 规则
- WHEN 执行 `mvnd validate -DskipBuiltinToolsDownload`
- THEN 构建成功，无 checkstyle violation 错误

#### Scenario: lint reports violations
- GIVEN 仓库中存在 checkstyle 风格违规
- WHEN 执行 `mvnd validate -DskipBuiltinToolsDownload`
- THEN checkstyle 报告违规详情，开发者可据此修正

### Requirement: standard-unit-test-command
仓库 MUST 提供标准单元测试命令，运行全部单元测试且不运行集成测试。

#### Scenario: unit tests run without integration tests
- GIVEN 仓库中同时存在单元测试和集成测试
- WHEN 执行 `mvnd test -DskipBuiltinToolsDownload`
- THEN 仅运行未标注 `@Tag("integration")` 或 `@Tag("e2e")` 的测试类，集成测试不被执行

### Requirement: standard-integration-test-command
仓库 MUST 提供标准集成测试命令，单独运行全部集成测试。

#### Scenario: integration tests run in isolation
- GIVEN 仓库中存在标注 `@Tag("integration")` 的测试类
- WHEN 执行 `mvnd test -Pintegration -DskipBuiltinToolsDownload`
- THEN 仅运行标注 `@Tag("integration")` 的测试类

### Requirement: integration-test-tagging
所有集成测试类 MUST 标注 JUnit 5 `@Tag("integration")`，以便构建系统正确分层执行。集成测试的识别标准为：依赖嵌入式 Lealone DB、文件系统、子进程，或跨层协作的测试。

#### Scenario: integration test excluded from unit run
- GIVEN 一个测试类标注了 `@Tag("integration")`
- WHEN 执行标准单元测试命令 `mvnd test -DskipBuiltinToolsDownload`
- THEN 该测试类不被执行

#### Scenario: integration test included in integration run
- GIVEN 一个测试类标注了 `@Tag("integration")`
- WHEN 执行标准集成测试命令 `mvnd test -Pintegration -DskipBuiltinToolsDownload`
- THEN 该测试类被执行

### Requirement: test-layering-convention
仓库 MUST 遵循三层测试分层约定，每层有明确的边界定义：
- **unit**：快速、无外部 I/O、不依赖数据库或外部进程；不标注 tag，默认由 `mvnd test` 运行
- **integration**：依赖嵌入式数据库、文件系统、子进程或跨层调用；标注 `@Tag("integration")`
- **e2e**：通过 HTTP/JSON-RPC 公开端点驱动的端到端验证；标注 `@Tag("e2e")`（约定预留，当前无此类测试）

#### Scenario: new test placed in correct layer
- GIVEN 开发者为一个依赖 Lealone DB 的功能编写测试
- WHEN 该测试类被添加到仓库
- THEN 该测试类标注 `@Tag("integration")` 并可通过 `mvnd test -Pintegration` 运行
