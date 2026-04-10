# Design: loop-recommend-auto-pipeline

## Approach

采用可插拔流水线架构，将推荐与执行解耦：

1. **LoopPipeline 接口** — 定义单次迭代的执行契约：`execute(LoopCandidate, LoopConfig) → IterationResult`。`DefaultLoopDriver` 通过依赖注入获取 pipeline 实例，保持调度器与执行逻辑解耦。

2. **SpecDrivenPipeline 实现** — 按 `PipelinePhase` 枚举定义的 5 个阶段顺序执行。每个阶段：
   - 从 classpath 加载阶段专属系统提示词模板
   - 构建 user prompt（注入候选 change 名称、milestone 上下文、项目根路径）
   - 创建 `Conversation`（system + user message）
   - 组装 `SimpleAgentContext`（工具集 + 会话 + 工作目录）
   - 通过 `Orchestrator` 运行 LLM 工具调用循环
   - 验证阶段输出（文件存在性检查）

3. **DefaultLoopDriver 修改** — 构造函数新增 `LoopPipeline` 参数。`runLoop()` 中将桩替换为 `pipeline.execute(candidate, config)` 调用，根据返回的 `IterationResult.status` 决定后续行为（继续/停止/跳过）。

4. **错误分类** — `SpecDrivenPipeline` 捕获异常并映射：
   - 正常完成 → `SUCCESS`
   - 阶段输出验证失败或 LLM 返回错误 → `FAILED`（附带 failureReason）
   - 超时中断 → `TIMED_OUT`
   - 调度器或配置层面的跳过 → `SKIPPED`

5. **阶段指令** — 5 个文本模板作为 classpath 资源（`/loop-phases/propose.txt` 等），包含该阶段的目标描述、输入输出契约和工具使用指引。运行时加载，注入候选上下文变量。

## Key Decisions

- **Pipeline 作为接口而非直接实现** — 便于测试时注入 mock pipeline，也允许未来替换执行策略（如远程执行、并行执行）。
- **复用 Orchestrator + LlmClient + tools 模式** — 与 `SkillServiceExecutor` 一致，不引入新的执行机制。工具集复用标准 6 件套（bash, read, write, edit, glob, grep）。
- **阶段指令作为 classpath 资源** — 而非硬编码字符串。便于后续迭代优化提示词，且与现有 skill instruction store 的理念一致。
- **超时用 iterationTimeoutSeconds 整体控制** — 不为每个阶段单独设超时。单次迭代的总时长由 `LoopConfig` 已有字段控制。
- **不在本 change 实现 LLM provider 初始化** — `SpecDrivenPipeline` 的构造函数接受 `LlmClient` 工厂，复用 `SkillServiceExecutor.DefaultSkillLlmClientFactory` 的配置发现逻辑。

## Alternatives Considered

- **将每个阶段注册为独立 skill** — 可行但增加 Lealone SERVICE 注册和调用的复杂度。首期作为 pipeline 内部阶段更简单，后续可按需拆分。
- **直接在 DefaultLoopDriver 内联执行逻辑** — 违反单一职责，且无法独立测试执行流程。Pipeline 接口抽象成本很低。
- **为每个阶段设置独立超时** — 过度设计。首期用迭代总超时，后续如需要可在 `LoopConfig` 中增加 per-phase 配置。
