# M31 - ORM 集成增强

## Goal

以减少重复 JDBC 样板代码和统一数据访问行为为主要目标，评估并逐步引入 Lealone ORM；对确实依赖数据库生成主键的场景，可额外利用 `Model.insert()` 自动回填主键能力（`962b771`），但不强制把所有 Store 都围绕主键回填改造。

## In Scope

- Lealone ORM Model 基类适配：定义本项目领域实体的 Model 子类
- 选择 1-2 个重复 CRUD 逻辑明显、迁移收益可衡量的 Store 作为 ORM 试点
- 对使用数据库生成主键的实体验证 Model.insert() 后 rowId/mainIndexColumn 正确设置；对外部提供主键的实体不强行围绕主键回填迁移
- JDBC 与 ORM 混合共存模式：已迁移的 Store 用 ORM，未迁移的保持 raw JDBC
- ORM 迁移的增量验证测试

## Out of Scope

- 全量 Store 迁移（分批进行，本 milestone 仅覆盖高价值目标）
- 复杂关联查询（JOIN、子查询）的 ORM 映射
- 数据库 Schema 变更管理（migration 工具）
- 从 Lealone ORM 迁移到其他 ORM 框架
- 为了抽象而抽象地引入宽泛的泛型 ORM 基类

## Done Criteria

- 至少一个重复 CRUD 逻辑明显且收益可衡量的 Store MUST 完成 ORM 试点迁移
- 若某个候选 Store 不依赖数据库生成主键，其迁移价值 MUST 以样板代码减少和行为一致性为主，而不是为了主键回填强行迁移
- 迁移后的 Store MUST 保持原有接口签名不变
- 对使用数据库生成主键的迁移对象，Model.insert() 回填的主键 MUST 与原有行为一致
- 未迁移的 Store（如 LealoneSessionStore、LealoneLlmCache）继续正常工作，不受影响
- 有迁移验证测试对比 ORM 与 raw JDBC 的读写结果一致性
- 试点结束后 MUST 产出继续迁移/停止迁移的准入标准，避免无收益扩张 ORM 覆盖面

## Planned Changes

- `orm-model-mappings` - Declared: planned - 定义试点领域实体所需的最小 ORM Model 映射与注解约定，映射到现有 Lealone DB 表结构
- `delivery-log-orm-pilot` - Declared: planned - 将 LealoneDeliveryLogStore 作为优先试点迁移到 ORM，并验证行为一致性与样板代码收益
- `question-store-feasibility` - Declared: planned - 评估 LealoneQuestionStore 迁移到 ORM 的真实收益；仅在收益明确且不引入额外复杂度时推进迁移
- `orm-jdbc-coexistence` - Declared: planned - 确保 ORM Store 与 raw JDBC Store 在同一数据库实例上混合共存，事务一致性不受影响
- `orm-adoption-guidelines` - Declared: planned - 形成 ORM 继续推广的准入标准、适用场景和 escape hatch 规则

## Dependencies

- M17 Lealone DB 缓存层（表结构已建立）
- Lealone ORM 模块（lealone-orm, Model 基类）
- Lealone 更新：`962b771` Model.insert 后自动设置 mainIndexColumn 的 rowId
- 已有 Store 接口（DeliveryLogStore、QuestionStore 等保持向后兼容）

## Risks

- ORM 抽象可能隐藏性能问题（N+1 查询、缺失索引等）
- Lealone ORM 的 Model 基类可能与本项目已有的领域模型产生命名冲突
- 迁移过程中若接口签名变化，可能影响上层模块
- ORM 的事务边界与 raw JDBC 手动事务管理的协调复杂度
- 若在试点阶段过早引入通用 ORM 基类，可能先得到额外抽象层而不是真实收益

## Status
- Declared: proposed

## Notes

- 迁移顺序建议：先 DeliveryLogStore（写频度高、insert 后取 ID 需求明确）→ 再 QuestionStore → 其他按需
- 应保留 escape hatch：允许在需要时降级到 raw PreparedStatement 执行复杂 SQL
- 不强制全部迁移——对于 LealoneLlmCache 这种高度优化过的 Store，raw JDBC 可能更合适
- 该里程碑首先是一次受控试点；只有当试点证明收益明确时，才适合扩大 ORM 覆盖面
