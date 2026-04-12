# M27 - 智能上下文注入与 Token 优化

## Goal

参考 Lealone `Service.genJavaCode()` 的反射筛选策略，在 LLM 调用前对 Conversation 历史和 ToolResult 做智能相关性裁剪与语义压缩，在不破坏循环恢复、答题恢复等关键上下文的前提下降低 Token 消耗、提升 LLM 响应质量，替代当前 `ContextWindowManager` 的简单截断策略。

## In Scope

- ToolResult 相关性裁剪：基于当前 turn 的 tool call 列表，只保留相关工具的输出结果到 LlmRequest
- Conversation 历史语义摘要：对早期消息做滑动窗口摘要压缩，保留最近 N 轮完整历史 + 摘要
- ContextRelevanceScorer 接口：定义上下文相关性评分契约，支持可插拔实现
- 与现有 CachingLlmClient 和 ContextWindowManager 的集成
- 恢复执行、Question 升级与 Answer 回放所需的不可裁剪上下文保留规则
- Token 使用量前后对比指标（命中率、压缩率）

## Out of Scope

- 语义级 prompt 相似度匹配（M17 已覆盖精确匹配缓存）
- Provider 层的流式处理（M19）
- 多模态内容（图片、音频）的上下文裁剪
- 分布式上下文管理

## Done Criteria

- 发送给 LLM 的 ToolResult MUST 默认只包含当前 turn 相关输出；被标记为恢复执行、Question 处理或审计回放必需的上下文 MUST 保留
- Conversation 历史 MUST 在超过阈值时触发摘要压缩，而非简单截断丢弃
- ContextRelevanceScorer 接口可被替换为不同实现（关键词匹配 / embedding 相似度 / 规则引擎）
- 智能注入层 MUST 作为透明装饰器插入 LlmClient 和 Orchestrator 之间，不改变上层调用签名
- 有单元测试覆盖相关性裁剪、摘要压缩、装饰器集成、边界条件场景
- MUST 定义固定评测集和验收阈值，用于比较上下文优化前后的关键任务表现
- 有对比基准测试证明 Token 减少量（目标 ≥30%）
- 在固定评测集上，关键任务表现 MUST 满足预先定义的无回归阈值

## Planned Changes
- `context-relevance-scorer` - Declared: complete - 定义 ContextRelevanceScorer 接口与默认关键词匹配实现，提供 ToolResult→turn 的相关性评分能力
- `context-retention-policy` - Declared: complete - 定义恢复执行、Question 升级和 Answer 回放场景下不可裁剪的最小上下文保留规则
- `tool-result-filter` - Declared: complete - 实现 ToolResultFilter 装饰器：在构建 LlmRequest 时根据当前 turn 的 tool call 列表过滤无关 ToolResult
- `conversation-summarizer` - Declared: complete - 实现 ConversationSummarizer：基于滑动窗口对早期对话历史生成语义摘要，替换简单截断策略
- `smart-context-injector` - Declared: planned - 整合上述组件为 SmartContextInjector 装饰器，透明包装 LlmClient，与 DefaultOrchestrator 和 LoopDriver 集成，并接入固定评测集验证

## Dependencies

- M4 Agent 生命周期与编排（Orchestrator 循环结构）
- M5 LLM Provider Layer（LlmClient 接口、LlmRequest 构建）
- M17 Lealone DB 缓存层（可复用缓存存储摘要）
- M19 LLM Streaming & Token Management（Token 计数基础）
- Lealone 更新：`ecfbce5` 依据上下文发送 public 字段/方法的策略参考

## Risks

- 相关性评分不准确可能导致关键上下文被误裁剪，影响 LLM 理解
- 摘要压缩可能丢失细节信息，导致后续 turn 出现事实错误
- 装饰器链路增加延迟，需控制摘要生成的计算开销
- 不同类型任务（代码生成 vs 问题回答 vs 数据分析）的最佳裁剪策略可能差异较大
- 若缺少固定评测集和无回归阈值，后续 change 很难判断优化是否真正有效

## Status
- Declared: proposed

## Notes

- 首期 ContextRelevanceScorer 实现使用简单的 tool name 匹配 + 关键词提取，后续可升级为 embedding 相似度
- 摘要生成本身消耗 token，需设置合理的摘要 budget（如不超过原文本的 20%）
- 参考 Lealone Service.genJavaCode() 中通过反射获取 public field/method 并按 propertyClasses 去重的思路
- 该 milestone 是 M24/M26 自主循环长运行稳定性的关键支撑——context 效率直接影响循环可持续性
- 首期优先保证“少裁错”而不是“极限压缩”，避免为追求压缩率牺牲恢复稳定性

