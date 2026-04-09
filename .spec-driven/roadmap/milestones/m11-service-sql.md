# M11 - Service SQL + Skill 嵌入

## Goal

将 auto-spec-driven 的 skill 系统拆解为 Lealone CREATE SERVICE SQL 格式，通过插件 SPI 扩展实现 skill 执行引擎，不修改 Lealone 基础仓库。

## In Scope

- SKILL.md → CREATE SERVICE SQL 的分解规范与转换工具
- ServiceExecutorFactory 插件实现 skill agent 循环引擎
- TypeScript CLI 的 Java ProcessBuilder 桥接层
- skill 指令体存储与渐进加载机制
- skills/ 目录自动发现并批量生成 DDL

## Out of Scope

- 具体 skill 指令内容开发（仅提供框架）
- Lealone 核心仓库修改
- MCP 协议（M10）
- LLM 后端实现（M5）
- SDK 公共 API 封装（M12）

## Done Criteria

- 可将 SKILL.md frontmatter 自动转换为 CREATE SERVICE SQL 语句
- 通过 PARAMETERS 配置 allowed_tools、scripts、instructions 等元数据
- ServiceExecutorFactory 插件通过 SPI 注册，不修改 Lealone core
- agent 循环能加载指令体、绑定 Tool 实例、执行 LLM 推理并返回结果
- TypeScript CLI 的 12 个子命令全部用 Java 改写，无 Node.js 外部依赖
- 支持 3 级渐进加载：元数据（始终在 context）→ 指令体（触发时加载）→ 脚本资源（按需执行）
- 自动扫描 skills/ 目录并批量生成 DDL 注册
- 有单元测试验证 SKILL.md → SQL 转换和插件注册链路

## Planned Changes
- `skill-sql-schema` - Declared: complete - SKILL.md → SQL 分解规范、参数映射规则、SQL 模板生成工具
- `skill-executor-plugin` - Declared: complete - ServiceExecutorFactory SPI 实现：加载 PARAMETERS 配置、构建 agent 循环、绑定 allowed-tools 到 M1 Tool 实例
- `skill-cli-java` - Declared: planned - 用 Java 改写 spec-driven.ts 全部 12 个子命令（propose/apply/verify/archive/cancel/init/list 等），实现全内置无外部依赖
- `skill-instructions-store` - Declared: complete - 指令体外部文件管理，3 级渐进加载机制（元数据→指令体→脚本资源）
- `skill-auto-discovery` - Declared: complete - 扫描 skills/ 目录解析 SKILL.md frontmatter，批量生成 CREATE SERVICE DDL 并执行注册

## Dependencies

- M1 核心接口（Tool 接口，service method 需映射为 Tool）
- M2 基础工具集（skill executor 需绑定 Read/Write/Edit/Bash/Glob/Grep 实例）
- M5 LLM 后端（agent 循环依赖 LLM 推理能力）
- Lealone SQL 引擎（CREATE SERVICE 语法、PARAMETERS 子句、ServiceExecutorFactory SPI）
- [auto-spec-driven](https://github.com/kw12121212/auto-spec-driven)（内嵌 skill 源：18 个 SKILL.md + scripts/spec-driven.ts 逻辑，TypeScript CLI 用 Java 改写，实现全内置）

## Risks

- PARAMETERS 值长度需验证：长 allowed_tools 列表和 scripts 路径可能超出解析限制
- agent 循环端到端测试需 M5 就绪，否则只能 mock LLM 层
- Lealone ServiceLoader SPI 类加载隔离：插件 JAR 需正确放入 classpath
- spec-driven.ts 改写为 Java 需保持行为一致性（12 个子命令的 JSON 输出格式、退出码语义）

## Status

- Declared: proposed

## Notes

- SKILL.md 拆解为 SQL 的映射：frontmatter → PARAMETERS + COMMENT，方法签名 → execute(params) return varchar，指令体 → PARAMETERS 引用的外部文件
- 不修改 Lealone 核心：所有扩展通过 ServiceExecutorFactory、ServiceCodeGenerator 的 SPI 插件实现，注册到 META-INF/services/
- 不依赖 TypeScript/Node.js：spec-driven.ts 的全部逻辑用 Java 改写，实现纯 Java 全内置
- Lealone 已验证支持：COMMENT 多行无大小限制、PARAMETERS 任意 string 键值对、ServiceLoader 插件机制
- 这是本 SDK 区别于 Go 版本的独有特性——利用 Lealone 原生 SQL + SPI 提供声明式 skill 定义
- 可与 M10（MCP）并行开发，两者都是工具集成层但机制不同



