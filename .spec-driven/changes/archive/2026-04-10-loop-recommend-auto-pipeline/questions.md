# Questions: loop-recommend-auto-pipeline

## Open

<!-- No open questions -->

## Resolved

- [x] Q: 阶段指令模板的详细内容应如何编写？
  Context: 模板内容直接影响 LLM 执行效果
  A: 完全参考 auto-spec-driven 的 spec-driven-propose/apply/verify/review/archive 各技能的 SKILL.md 内容，将各技能的核心步骤转化为对应阶段的系统提示词

- [x] Q: VERIFY 阶段验证失败时是否应自动重试？
  Context: 影响流水线容错能力和 token 消耗
  A: 参考 spec-driven-verify 技能行为——检测到 CRITICAL 问题时报告失败并停止，不自动重试。返回 FAILED 并附带失败原因

- [x] Q: LlmClient 工厂是否复用 SkillServiceExecutor.DefaultSkillLlmClientFactory？
  Context: 复用减少重复但引入内部类依赖
  A: 接受建议——构造函数接受 Function<Path, LlmClient> 参数，SpecDrivenPipeline 自身不依赖 SkillServiceExecutor 内部类
