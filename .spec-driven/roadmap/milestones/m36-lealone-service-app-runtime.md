# M36 - Lealone 原生服务应用运行时

## Goal

承接 Lealone README 中“通过 `services.sql` 直接运行企业级 AI 应用”的方向，在当前 skill SQL、HTTP API 和平台整合基础上，为本项目补齐声明式应用启动、服务暴露和运行打包约定，使仓库不仅能运行 agent API，也能按 Lealone 原生服务模型运行面向业务的应用服务。

## In Scope

- 声明式应用启动入口：从 `services.sql` 或等价声明式材料完成幂等初始化与启动
- 基于 Lealone Service 的应用服务暴露契约，覆盖服务方法到 HTTP 调用入口的稳定映射
- 面向部署和运维的最小运行打包约定，减少手工拼装底层启动参数
- 启动期 schema、service、运行配置的最小治理边界，避免声明式启动破坏既有数据或行为
- 与现有 `SpecDriven` SDK、JSON-RPC、`/api/v1/*` agent API 的并存兼容规则

## Out of Scope

- 多节点集群、分片、复制或跨地域部署拓扑
- 通用低代码页面生成器或可视化应用设计器
- 完整 DevOps 发布平台或多环境流水线编排
- 新的非 Lealone 服务模型或与 Lealone 无关的通用应用容器抽象
- 业务流程级的人机协作、审计恢复编排（由后续 M37 承接）

## Done Criteria

- 系统 MUST 支持从一个受支持的声明式应用入口加载 service/schema/runtime 配置并完成幂等启动
- 应用服务 MUST 能通过稳定的 HTTP 入口被调用，且其契约与底层 Lealone Service 方法映射保持一致
- 应用启动打包与运行说明 MUST 明确开发态与部署态的最小入口，不要求操作者手工拼装底层组件初始化顺序
- 启动期 schema/service 初始化 MUST 明确声明允许的幂等操作与失败边界，不得静默破坏已有数据或不兼容对象
- 已有 `/api/v1/*` agent API、SDK 和 JSON-RPC 能力 MUST 保持兼容，不因应用服务运行时引入而改变既有行为
- 自动化测试 MUST 覆盖声明式启动 happy path、重复启动幂等、服务 HTTP 暴露、启动期治理拒绝和与既有 agent API 并存场景

## Planned Changes
- `service-app-bootstrap` - Declared: complete - 定义从 `services.sql` 或等价声明式入口加载 schema、service 与运行配置的应用启动流程，并保证重复执行可幂等收敛
- `service-http-exposure` - Declared: planned - 为受支持的 Lealone Service 提供稳定的应用级 HTTP 暴露契约，明确方法映射、参数传递和错误返回边界
- `service-runtime-packaging` - Declared: planned - 定义应用运行时的最小打包与启动约定，减少部署时对底层 Lealone 初始化细节的手工拼装
- `service-schema-bootstrap-governance` - Declared: planned - 为启动期建表、建服务和运行配置装配增加最小治理规则，限制不安全或不兼容的声明式变更进入自动启动链路

## Dependencies

- M11 Service SQL + Skill 嵌入（提供 `CREATE SERVICE` 与 SPI 基础）
- M14 HTTP REST API（复用 HTTP 服务暴露与鉴权/错误处理经验）
- M16 集成与发布（提供面向运行产物的基础发布材料）
- M25 生产环境一键安装与修复（作为部署和运行入口的运维承载层）
- M32 Lealone 平台化统一基础设施（提供统一初始化、配置与健康检查底座）

## Risks

- 若把声明式启动能力做得过宽，容易把应用初始化入口演变成缺乏治理的“任意执行脚本”通道
- Service 方法到 HTTP 暴露的契约若定义含糊，可能与现有 `/api/v1/*` agent API 形成边界冲突
- 启动期 schema/service 幂等规则若不清晰，重复启动可能导致对象漂移或误判成功
- 若运行打包约定与 Lealone 平台层职责混淆，M32 与本里程碑会发生范围重叠

## Status
- Declared: proposed

## Notes

- 本里程碑直接对应 Lealone README 中“零代码零需求文档渐进式开发企业级 AI 应用”和“通过需求文档直接运行企业级 AI 应用”的增强方向
- 这里的“应用服务运行时”强调面向业务能力的 service 暴露，不替代现有 agent SDK、JSON-RPC 或 `/api/v1/*` 管理面 API
- 若后续要支持 `services.sql` 之外的其他声明式入口，应先证明其能复用同一套治理与幂等边界，而不是平行增加多种启动模型

