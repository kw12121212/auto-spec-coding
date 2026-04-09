# skill-sql-schema

## What

SKILL.md YAML frontmatter → Lealone CREATE SERVICE SQL 转换工具。解析 SKILL.md 的 frontmatter，按映射规则生成标准 DDL 语句，为后续 skill-executor-plugin 和 skill-auto-discovery 提供基础。

## Why

M11 的核心目标是将 auto-spec-driven 的 skill 系统映射到 Lealone CREATE SERVICE SQL 格式。本 change 建立分解规范和转换工具，使 SKILL.md 可以被自动转换为 Lealone 可执行的 DDL。这是 M11 其余 4 个 planned change 的前提依赖。

## Scope

- SKILL.md YAML frontmatter 解析器（SkillMarkdownParser）
- frontmatter 字段 → CREATE SERVICE SQL 子句的映射规则
- SQL 模板生成器（SkillSqlConverter）
- 支持 PARAMETERS 映射：skill_id、type、version、author、instructions_path
- 指令体（frontmatter 后的 markdown）提取逻辑
- 单元测试验证转换正确性

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
