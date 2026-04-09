# Design: skill-sql-schema

## Approach

创建一个纯 Java 的 SKILL.md → CREATE SERVICE SQL 转换器，不依赖 Lealone 运行时，仅生成 SQL 字符串。

## Key Decisions

1. **纯字符串生成**: 不引入 Lealone 运行时依赖，converter 输出 SQL 字符串，由调用方负责执行
2. **YAML 解析使用 snakeyaml**: Lealone 已引入 snakeyaml 作为依赖，无需额外添加
3. **单方法服务模型**: 每个 skill 映射为 `execute(prompt varchar) varchar` 单方法服务
4. **指令体内联存储**: 指令体直接作为 PARAMETERS 'instructions' 存入 DB，无需外部文件引用

## Components

### SkillFrontmatter (record)

解析后的 frontmatter 数据载体：
- skillId (String) — skill_id → snake_case 标识
- name (String) — kebab-case 服务名
- description (String) — → COMMENT
- author (String) — → PARAMETERS
- type (String) — → PARAMETERS
- version (String) — → PARAMETERS

### SkillMarkdownParser

从 SKILL.md 文件提取 frontmatter 和指令体：
- 检测 `---` 边界，提取 YAML 块
- 解析 YAML 为 SkillFrontmatter（使用 snakeyaml）
- 提取第二个 `---` 后的全部文本作为 instructionBody

### SkillSqlConverter

将 SkillFrontmatter 转换为 CREATE SERVICE SQL。

映射规则：
| SKILL.md 字段 | SQL 子句 |
|---|---|
| name | 服务名 (CREATE SERVICE name) |
| description | COMMENT |
| skill_id | PARAMETERS 'skill_id' '...' |
| type | PARAMETERS 'type' '...' |
| version | PARAMETERS 'version' '...' |
| author | PARAMETERS 'author' '...' |
| (指令体) | PARAMETERS 'instructions' '...' — 直接存储在 DB 内 |

### 完整 SQL 输出示例

输入 SKILL.md:
```yaml
---
skill_id: spec_driven_propose
name: spec-driven-propose
description: Propose a new spec-driven change.
author: auto-spec-driven
type: agent_skill
version: 1.0.0
---
(instruction body)
```

输出 SQL:
```sql
CREATE SERVICE IF NOT EXISTS `spec-driven-propose` (
    execute(prompt varchar) varchar
) COMMENT 'Propose a new spec-driven change.'
LANGUAGE 'java'
PACKAGE 'org.specdriven.skill'
IMPLEMENT BY 'org.specdriven.skill.executor.SpecDrivenProposeExecutor'
PARAMETERS 'skill_id' 'spec_driven_propose', 'type' 'agent_skill', 'version' '1.0.0', 'author' 'auto-spec-driven', 'instructions' 'You are helping the user create a new spec-driven change proposal...'
```

## Package Structure

```
org.specdriven.skill.sql
├── SkillFrontmatter.java      // 解析后的 frontmatter record
├── SkillMarkdownParser.java   // SKILL.md 文件解析器
├── SkillSqlConverter.java     // SQL 生成器
└── SkillSqlException.java     // 异常类型
```

## Error Handling

- YAML 解析失败 → 抛出 SkillSqlException，包含文件路径和解析错误原因
- 缺少必需字段（skill_id, name） → 抛出 SkillSqlException
- description 为空 → COMMENT 省略

## Dependencies

- snakeyaml（Lealone 已引入）
- Java 标准库
- 无 Lealone 运行时依赖

## Alternatives Considered

1. **外部文件引用**: 原方案通过 instructions_path 引用外部文件，改为直接内联到 PARAMETERS 更简单，且 Lealone PARAMETERS 支持任意长度字符串。
2. **多方法服务**: 每个 skill 映射多个方法（如 init、execute、cleanup）。当前 skill 模型只需 execute 单入口，多方法增加复杂性无收益。
