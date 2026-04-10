# Design: loop-progress-persistence

## Approach

遵循项目已有的 Lealone Store 模式（`LealoneTaskStore`、`LealoneDeliveryLogStore` 等），新增 `LoopIterationStore` 接口及其 Lealone 实现，然后将其集成到 `DefaultLoopDriver` 中。

### 存储模型

两张表：

1. **`loop_iterations`** — 每次迭代的执行记录
   - `iteration_number` INT PRIMARY KEY
   - `change_name` VARCHAR(500) NOT NULL
   - `milestone_file` VARCHAR(500) NOT NULL
   - `started_at` BIGINT NOT NULL
   - `completed_at` BIGINT
   - `status` VARCHAR(20) NOT NULL（枚举名）
   - `failure_reason` CLOB

2. **`loop_progress`** — 单行循环级别快照
   - `id` INT PRIMARY KEY（固定值 1）
   - `loop_state` VARCHAR(20) NOT NULL
   - `completed_change_names` CLOB（JSON 数组）
   - `total_iterations` INT NOT NULL DEFAULT 0
   - `updated_at` BIGINT NOT NULL

### DefaultLoopDriver 集成

- 新增四参数构造函数 `DefaultLoopDriver(LoopConfig, LoopScheduler, LoopPipeline, LoopIterationStore)`
- 启动时调用 `store.loadProgress()` 恢复已完成 change 列表和循环状态
- 每次迭代完成后调用 `store.saveIteration(iteration)` 和 `store.saveProgress(snapshot)` 持久化进度
- 不传入 store 时降级为纯内存模式（现有行为不变）

## Key Decisions

1. **采用 Lealone 嵌入式 DB 而非 JSON 文件** — 与项目已有 5+ 个 Store 实现保持架构一致性，且 SQL 查询能力为 M26 恢复场景提供基础
2. **Store 作为可选构造参数** — 不破坏现有两参数和三参数构造函数的向后兼容性
3. **单行 progress 表** — 循环只有一个全局进度状态，不需要多行查询
4. **`completed_change_names` 存为 JSON 数组** — 简单且与项目中已有的 JSON 序列化工具一致（vertx `JsonObject`）
5. **不在 Store 中管理 LoopState 转换** — 状态机逻辑仍由 `DefaultLoopDriver` 控制，Store 只负责读写

## Alternatives Considered

1. **JSON 文件存储**（`.spec-driven/loop-state/`）— 实现简单但不支持查询，与项目架构不一致
2. **扩展 `LoopIteration` record 添加 `id` 字段** — `iterationNumber` 已是天然主键，不需要额外 UUID
3. **将进度嵌入现有 `task-registry` 表** — 循环进度与任务管理是不同关注点，混存会增加耦合
