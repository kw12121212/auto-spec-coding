# test-command-quality-gates

## What

为仓库建立标准化的 lint/validation、单元测试、集成测试验证命令约定，并将这些约定以可观察行为形式记录到测试规范中。具体包括：在 pom.xml 中引入 maven-checkstyle-plugin 作为 lint 入口；为现有集成测试类添加 JUnit 5 `@Tag("integration")` 标注；配置 maven-surefire-plugin 默认排除集成测试 tag；提供 `-Pintegration` Maven profile 专门运行集成测试；更新测试规范记录三层测试分层约定和标准命令。

## Why

M40 Done Criteria 明确要求仓库提供至少一组受支持的 lint/validation 命令以及 unit/integration test 命令，作为测试相关变更的标准验证入口。目前仓库：
- 没有在 Maven 构建中配置 checkstyle/lint；
- 单元测试和集成测试混跑于同一 surefire 执行中，无法单独触发；
- 没有任何文档或规范记录"什么命令应该用于验证测试相关变更"。
本变更补齐这一缺口，使后续 change 可直接引用稳定的验证入口，而不必每次重新发现命令。

## Scope

**In Scope**
- pom.xml 中新增 maven-checkstyle-plugin（bind 到 validate phase，使用 Google Java Style 规则集）
- 为所有 `*IntegrationTest.java` 和 `integration/` 包下的测试类添加 `@Tag("integration")`
- 配置 surefire 默认通过 `excludedGroups=integration` 排除集成测试
- 新增 Maven profile `integration`，在该 profile 下通过 `groups=integration` 单独运行集成测试
- 新增 delta spec `testing/test-quality-gates.md`，记录三层测试约定、标准命令和 tag 规范

**Out of Scope**
- 新增任何业务回归测试用例
- 外部 CI 平台或托管测试云集成
- 修改现有测试的断言逻辑或测试覆盖面
- 仅为美观而重构现有测试结构

## Unchanged Behavior

- 所有现有测试的通过/失败结果不得改变
- `mvnd test -DskipBuiltinToolsDownload` 运行后，之前通过的单元测试仍然通过
- 现有集成测试的行为语义不变，仅新增 `@Tag` 标注
