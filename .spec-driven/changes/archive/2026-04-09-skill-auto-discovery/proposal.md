# skill-auto-discovery

## What

扫描 `skills/` 目录，对每个包含 SKILL.md 的子目录解析 frontmatter、生成 CREATE SERVICE DDL 并通过 Lealone JDBC 执行注册。返回 `DiscoveryResult` 汇总成功/失败数量。

## Why

M11 的 Done Criteria 要求"自动扫描 skills/ 目录并批量生成 DDL 注册"。`skill-sql-schema` 已完成 SKILL.md → SQL 的转换工具链，本 change 是自然的下一步——打通从文件系统到 Lealone 数据库的完整注册管道，为后续 `skill-executor-plugin` 提供可运行的 service schema 基础。

## Scope

**In scope:**
- `SkillAutoDiscovery` — 接受 jdbcUrl 和 skillsDir，执行批量注册
- `DiscoveryResult` record — registeredCount, failedCount, errors 列表
- `SkillDiscoveryError` record — path, errorMessage（per-skill 失败信息）
- 单元测试：目录扫描逻辑、部分失败场景、错误收集行为
- 集成测试：对真实 skills/ 目录（条件跳过，目录不存在时 skip）

**Out of scope:**
- ServiceExecutorFactory SPI 实现（skill-executor-plugin）
- 3 级渐进加载机制（skill-instructions-store）
- CLI 子命令 Java 改写（skill-cli-java）
- 修改 SkillMarkdownParser / SkillSqlConverter / SkillSqlException

## Unchanged Behavior

- `SkillMarkdownParser`、`SkillSqlConverter`、`SkillSqlException` 的现有行为不变
- `org.specdriven.skill.sql` 包下已有类的公共 API 不变
