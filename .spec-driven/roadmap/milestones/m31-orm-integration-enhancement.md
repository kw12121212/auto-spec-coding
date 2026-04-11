# M31 - ORM 集成增强

## Goal

利用 Lealone Model.insert() 自动回填主键的能力（`962b771`），将项目中部分使用 raw JDBC 的 Store 层迁移到 Lealone ORM，减少样板代码、提升数据一致性，并为未来更复杂的查询需求奠定基础。

## In Scope

- Lealone ORM Model 基类适配：定义本项目领域实体的 Model 子类
- Store 层 ORM 迁移框架：ORMStore<T> 泛型基类，封装通用 CRUD 操作
- 高频写操作 Store 迁移：LealoneDeliveryLogStore、LealoneQuestionStore（insert 后需获取 ID 的场景优先）
- 主键回填验证：确保 Model.insert() 后 rowId/mainIndexColumn 正确设置
- JDBC 与 ORM 混合共存模式：已迁移的 Store 用 ORM，未迁移的保持 raw JDBC
- ORM 迁移的增量验证测试

## Out of Scope

- 全量 Store 迁移（分批进行，本 milestone 仅覆盖高价值目标）
- 复杂关联查询（JOIN、子查询）的 ORM 映射
- 数据库 Schema 变更管理（migration 工具）
- 从 Lealone ORM 迁移到其他 ORM 框架

## Done Criteria

- ORMStore<T> 基类 MUST 提供通用的 insert/update/delete/findById/findAll 操作
- LealoneDeliveryLogStore 和 LealoneQuestionStore MUST 迁移到 ORM 模式，insert 后自动获得主键 ID
- 迁移后的 Store MUST 保持原有接口签名不变（LealoneDeliveryLogStore / QuestionStore 接口不变）
- Model.insert() 回填的主键 MUST 与原来 SELECT LAST_INSERT_ID() 结果一致
- 未迁移的 Store（如 LealoneSessionStore、LealoneLlmCache）继续正常工作，不受影响
- 有迁移验证测试对比 ORM 与 raw JDBC 的读写结果一致性

## Planned Changes

- `orm-model-base` - Declared: planned - 定义项目领域实体共用的 ORM Model 基类与注解约定，映射到现有 Lealone DB 表结构
- `orm-store-generic` - Declared: planned - 实现 ORMStore<T> 泛型基类：封装通用 CRUD，支持条件查询与分页
- `delivery-log-orm-migration` - Declared: planned - 将 LealoneDeliveryLogStore 从 raw JDBC 迁移到 ORM，验证 insert 后主键回填
- `question-store-orm-migration` - Declared: planned - 将 LealoneQuestionStore 从 raw JDBC 迁移到 ORM，验证 insert 后主键回填
- `orm-jdbc-coexistence` - Declared: planned - 确保 ORM Store 与 raw JDBC Store 在同一数据库实例上混合共存，事务一致性不受影响

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

## Status
- Declared: proposed

## Notes

- 迁移顺序建议：先 DeliveryLogStore（写频度高、insert 后取 ID 需求明确）→ 再 QuestionStore → 其他按需
- ORMStore 应保留 escape hatch：允许在需要时降级到 raw PreparedStatement 执行复杂 SQL
- 不强制全部迁移——对于 LealoneLlmCache 这种高度优化过的 Store，raw JDBC 可能更合适
- 该里程碑的价值不仅是代码简化，更是为未来复杂查询（统计报表、关联分析）铺路
