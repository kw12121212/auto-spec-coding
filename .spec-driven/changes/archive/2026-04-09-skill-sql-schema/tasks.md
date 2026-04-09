# Tasks: skill-sql-schema

## Implementation

- [x] T1: 创建 SkillSqlException 异常类 (org.specdriven.skill.sql)
- [x] T2: 创建 SkillFrontmatter record (skillId, name, description, author, type, version)
- [x] T3: 创建 SkillMarkdownParser — 解析 SKILL.md 的 YAML frontmatter 和指令体
- [x] T4: 创建 SkillSqlConverter — 将 SkillFrontmatter 转换为 CREATE SERVICE SQL
- [x] T5: 单元测试 SkillMarkdownParser（正常 frontmatter、缺少必需字段、空指令体、description 含单引号）
- [x] T6: 单元测试 SkillSqlConverter（验证输出 SQL 结构、PARAMETERS 键值、COMMENT 转义、无 description 时省略 COMMENT）

## Testing

- [x] Validation: run `mvn compile -pl . -q` to verify compilation
- [x] Unit test: run `mvn test -pl . -Dtest="org.specdriven.skill.sql.*" -q` to execute converter tests

## Verification

- [x] Verify SkillMarkdownParser correctly parses all 19 SKILL.md files from auto-spec-driven reference project
- [x] Verify generated SQL is valid Lealone CREATE SERVICE syntax
