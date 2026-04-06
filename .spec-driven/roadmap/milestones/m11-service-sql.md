# M11 - Service SQL 集成

## Goal

利用 Lealone 原生 `CREATE SERVICE` SQL 语法，提供 SQL 驱动的 agent 能力定义机制，使开发者可通过 service.sql 声明式地定义工具和技能，作为 skill 系统的等价替代。

## In Scope

- service.sql 文件解析与加载（CREATE SERVICE / CREATE WORKFLOW 等 Lealone SQL 语法）
- SQL 定义的 Service 到 M1 Tool 接口的自动适配（每个 service method 映射为一个可调用 Tool）
- Service 参数绑定与结果序列化（SQL 类型 <-> Java 类型 <-> Tool 输入输出）
- Service SQL 文件的自动发现与热加载（从 classpath 或指定目录扫描 *.sql）
- 内置 skill 等价功能（预置常用 service.sql 模板，如代码生成、文件处理等）

## Out of Scope

- 具体 skill 内容的开发（仅提供框架和模板）
- MCP 协议（M10）
- SDK 公共 API 封装（M12）

## Done Criteria

- 可通过 service.sql 文件定义 agent 工具，并被 agent 编排循环正确调用
- service.sql 中定义的 service method 自动注册为 M1 Tool 接口实例
- 参数绑定正确处理基本类型、字符串、JSON 对象
- 支持从 classpath 和文件系统自动发现并加载 *.sql 文件
- 有单元测试验证 SQL 定义到 Tool 调用的完整链路

## Planned Changes

- `service-sql-loader` - Declared: planned - service.sql 文件解析、发现与加载机制实现
- `service-sql-tool-adapter` - Declared: planned - SQL Service 到 Tool 接口的自动适配层，参数绑定与结果序列化
- `service-sql-builtin-skills` - Declared: planned - 预置常用 skill 等价的 service.sql 模板集合

## Dependencies

- M1 核心接口（Tool 接口）
- M2 基础工具集（Service SQL 可调用基础工具）
- Lealone SQL 引擎（lealone-sql, lealone-server 的 CREATE SERVICE 能力）

## Risks

- Lealone CREATE SERVICE 的能力边界需确认，复杂逻辑可能需要 WORKFLOW 或 Java 回调补充
- SQL 语法的表达能力可能不足以覆盖所有 skill 场景，需设计合理的扩展机制

## Status

- Declared: proposed

## Notes

- 这是本 SDK 区别于 Go 版本的独有特性——利用 Lealone 原生 SQL 能力提供声明式工具定义
- service.sql 与 skill 功能等价但表达方式不同：skill 用代码，service.sql 用 SQL 声明
- 可与 M10（MCP）并行开发，两者都是工具集成层但机制不同
- Lealone 的 CREATE SERVICE / CREATE WORKFLOW 语法参考：https://github.com/lealone/Lealone
