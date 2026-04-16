# M32 - Lealone 平台化统一基础设施

## Goal

基于 M27-M31 的各项增强成果，为项目增加一个以 Lealone 为中心的薄整合层，统一数据库、LLM 运行时、动态编译、交互式 agent 四大能力域的初始化、配置、健康检查和度量采集，减少分散接入成本并最大化利用 Lealone 的原生能力。

## In Scope

- LealonePlatform 平台抽象层：统一的初始化、配置、生命周期管理入口
- 平台能力注册表：数据库、LLM、编译器、Agent 交互四大能力的统一发现与注册
- 统一配置中心：合并分散在各处的 Lealone 连接 URL、LLM 参数、编译路径等为单一配置源
- 平台健康检查：聚合各子系统的健康状态，提供统一的健康端点
- 平台能力度量：Token 使用量、编译次数、缓存命中率、交互次数等统一指标收集
- 向后兼容适配：确保现有代码无需大幅改动即可迁移到平台层

## Out of Scope

- 替换 Lealone 本身的功能（本里程碑是整合不是重写）
- 新增非 Lealone 能力的外部依赖
- 分布式/集群模式的多节点平台管理
- 声明式 `services.sql` 应用启动入口与应用级打包运行约定
- 面向业务应用的 `/service/<service>/<method>` 服务暴露契约
- 跨 service/tool/agent 的企业级 workflow 运行编排与治理
- 平台的可视化管理 UI
- 为未来非 Lealone 后端预先构建厚重的通用可移植抽象层
- 承诺“替换底层平台实现即可上层零改动”之类的可移植性目标

## Done Criteria

- LealonePlatform.start() / stop() / close() MUST 完整管理所有子系统生命周期
- 通过 LealonePlatform 可以获取 Database、LLM Compiler、InteractiveSession、ConfigCenter 的统一引用
- 所有 Lealone 相关配置 MUST 从单一配置源读取，不再散落在各个 Store/Client 的构造函数中
- 平台健康检查 MUST 聚合 DB 连接池、LLM endpoint、编译器、Agent 交互各子系统状态
- 平台度量指标 MUST 可通过 EventBus 或 HTTP API 查询
- 现有功能（SessionStore、LlmCache、PolicyStore 等）在迁移到平台层后行为不变
- 有集成测试验证平台启停、配置加载、健康检查、度量采集的完整性
- 平台层 MUST 保持为薄胶水层，不得为尚不存在的第二实现预先引入大范围抽象回填

## Planned Changes
- `lealone-platform-core` - Declared: complete - 定义 LealonePlatform 及最小能力注册机制，统一四大能力域（DB/LLM/Compile/Agent）的发现与启动入口
- `platform-config-lifecycle` - Declared: complete - 实现统一配置中心与生命周期管理：聚合 JDBC URL、LLM 参数、编译路径、Agent 配置，有序启停各子系统
- `platform-health-metrics` - Declared: complete - 实现聚合健康检查与度量指标收集：检测 DB/LLM/Compiler/Agent 状态，收集 Token 用量、编译次数、缓存命中率等指标
- `platform-migration-adapters` - Declared: complete - 编写最小向后兼容适配器：将现有分散的 Lealone 使用方式平滑迁移到平台层，不破坏上层 API

## Dependencies

- M27 智能上下文注入（LLM 能力域完善）
- M28 动态 LLM 配置（配置统一的前提）
- M29 交互式人机协作（Agent 能力域就绪）
- M30 动态编译与热加载（编译能力域就绪）
- M31 ORM 集成增强（DB 能力域深化）
- M17 Lealone DB 缓存层（现有数据库基础设施）
- M18 密钥保险库（统一配置中的 secret 引用支撑）

## Risks

- 平台抽象层可能过度设计，增加不必要的间接层
- 统一配置中心若设计不当，可能成为配置冲突的单点
- 生命周期管理的依赖顺序错误可能导致启动失败或资源泄漏
- 向后兼容适配器的维护成本可能随时间增长
- 若过早承诺通用平台可替换性，容易驱动不必要的抽象膨胀

## Status
- Declared: complete

## Notes

- 这是整个 Lealone 增强 roadmap 的"收尾"里程碑，不应在 M27-M31 任一完成前启动
- 平台层的核心理念是"约定优于配置" + "渐进式迁移"——不强求一步到位
- 设计原则：LealonePlatform 是薄胶水层，不是厚抽象层，每个能力域的实现仍在各自模块内
- Lealone README 中更偏应用运行时的 `services.sql`、`/service/...`、`workflow` 方向由后续 M36/M37 单独承接，避免把平台整合层做成超大杂糅里程碑
- 若平台层要在共享或生产环境暴露动态 LLM 配置或动态编译入口，应与相应治理 milestone（如 M33、M34）配套规划
- 若未来真的出现非 Lealone 平台诉求，应基于已验证的痛点另开 roadmap，而不是在本 milestone 里预支抽象成本
- 该里程碑完成后，项目的架构宣言可更新为："Built on Lealone Platform — unified DB, LLM runtime, compiler, and agent interaction"





