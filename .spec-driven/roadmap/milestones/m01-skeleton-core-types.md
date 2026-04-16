# M01 - 项目骨架与核心接口

## Goal

搭建 Maven 项目、构建系统和核心 Java 接口，引入 Lealone 依赖，为后续所有功能开发提供统一的类型基础和项目结构。

## In Scope

- Maven 项目初始化与 Lealone 依赖管理
- 项目目录结构约定（src/main/java, src/test/java）
- 构建、测试基础设施（JUnit 5）
- 核心接口定义（Tool, Agent, Event, PermissionProvider）
- 配置加载（YAML/Properties 解析，使用 Lealone 内置能力或 java.util.Properties）
- 结构化事件系统基础类型

## Out of Scope

- 具体工具实现
- Agent 运行时逻辑
- 接口传输层（JSON-RPC / HTTP）

## Done Criteria

- `mvnd compile` 和 `mvnd test` 通过
- 核心接口类型可在其他包中被引用
- 配置文件可被正确解析为 Java 对象
- 事件类型可被实例化并序列化为 JSON

## Planned Changes

- `project-scaffold` - Declared: complete - 初始化 Maven 项目、目录结构、pom.xml、Lealone 依赖引入
- `core-interfaces` - Declared: complete - 定义 Tool、Agent、Event、PermissionProvider 等核心 Java 接口
- `config-loader` - Declared: complete - 实现配置文件解析与 Java 对象映射
- `event-system-types` - Declared: complete - 定义结构化事件系统的核心类型

## Dependencies

- JDK 25+（VirtualThread 支持）
- Lealone 核心模块（lealone-common, lealone-net）

## Risks

- 核心接口设计需要兼顾后续多层接口（SDK / JSON-RPC / HTTP）的需求，过早固化可能产生返工
- Lealone 版本选择需稳定，避免 API 变更导致适配成本

## Status

- Declared: complete


## Notes

- 此里程碑是所有后续开发的基础，接口设计需与 spec-coding-sdk 的 Go 实现保持功能对等、设计对齐但不照搬
- 建议先完成 `project-scaffold` 和 `core-interfaces` 两个 change，再推进 `config-loader` 和 `event-system-types`
- PermissionProvider 接口在此里程碑定义，M02 工具集可据此预留权限检查钩子
