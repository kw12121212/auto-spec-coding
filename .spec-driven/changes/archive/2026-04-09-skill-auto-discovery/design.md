# Design: skill-auto-discovery

## Approach

新建 `org.specdriven.skill.discovery` 包，包含三个类型：`SkillDiscoveryError` record、`DiscoveryResult` record、`SkillAutoDiscovery`。

`SkillAutoDiscovery.discoverAndRegister()` 的执行流程：

1. `Files.list(skillsDir)` — 列出 skillsDir 的直接子目录
2. 过滤含有 `SKILL.md` 的子目录
3. 对每个 SKILL.md 调用 `SkillMarkdownParser.parse(path)`
4. 对解析结果调用 `SkillSqlConverter.convert(frontmatter, instructionBody)`
5. 通过 `DriverManager.getConnection(jdbcUrl)` + `stmt.executeUpdate(sql)` 执行 DDL
6. 捕获每个 skill 的 `SkillSqlException` 和 `SQLException`，收入 errors 列表继续处理
7. 返回 `DiscoveryResult(registered, failed, errors)`

目录级别失败（skillsDir 不可读、`Files.list` 抛 IOException）则直接抛 `SkillSqlException`，不继续。

## Key Decisions

1. **部分成功**: 单个 skill 失败不中止整批注册，错误收入 `DiscoveryResult.errors`。这与 M11 "批量生成" 语义一致，避免一个损坏的 SKILL.md 阻塞所有其他 skill 的注册。

2. **JDBC 连接模式**: 与 `LealoneTaskStore` 保持一致，per-operation 获取连接（`try-with-resources`），不做连接池管理——discovery 是低频启动时操作，连接开销可接受。

3. **仅扫描直接子目录**: `Files.list(skillsDir)` 不递归，与 auto-spec-driven 的 `skills/<skill-name>/SKILL.md` 固定结构保持一致。

4. **包隔离**: 新建 `org.specdriven.skill.discovery` 包，与 `org.specdriven.skill.sql` 分离，sql 包保持纯字符串生成、无运行时依赖。

5. **无 Lealone 运行时依赖于 sql 包**: SkillAutoDiscovery 在 discovery 包内引入 JDBC，sql 包继续保持零 Lealone 运行时依赖。

## Package Structure

```
org.specdriven.skill.discovery
├── SkillDiscoveryError.java    // record: path, errorMessage
├── DiscoveryResult.java        // record: registeredCount, failedCount, errors
└── SkillAutoDiscovery.java     // discoverAndRegister()
```

## Alternatives Considered

1. **SQL 执行器注入（函数式接口）**: 将 `Consumer<String>` 或 `SqlExecutor` 接口注入 SkillAutoDiscovery，便于单元测试时 mock SQL 执行。但已有模式（LealoneTaskStore）直接持有 jdbcUrl，且 discovery 场景下更关注 SKILL.md 解析和目录扫描的正确性，SQL 执行路径通过集成测试覆盖。放弃，与现有模式保持一致。

2. **递归目录扫描**: 支持多层嵌套 skills 目录。auto-spec-driven 的固定结构不需要此功能，YAGNI。
