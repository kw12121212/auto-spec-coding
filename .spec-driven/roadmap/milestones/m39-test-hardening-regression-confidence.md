# M39 - 测试补强与回归信心

## Goal

在现有 SDK、JSON-RPC、HTTP、service runtime 和 workflow 能力已经落地的基础上，系统化补强高风险测试面，提升跨接口行为一致性、运行时回归可发现性和复杂状态流转场景的发布信心。

## In Scope

- 仅补强已有 spec 已定义之外部可观察行为的测试空白，不扩大功能范围
- service runtime、service HTTP exposure、启动配置校验与错误传播的回归测试补强
- SDK / HTTP / JSON-RPC 三层接口围绕相同公共能力的外部行为一致性验证补强
- workflow 暂停恢复、checkpoint 恢复、重试耗尽、审计事件等复杂生命周期回归测试补强
- 仅为上述回归测试提供最小必要、局部直接服务的测试夹具、断言辅助或稳定性修复

## Out of Scope

- 新增产品功能或扩大既有功能范围
- 通用测试框架整理或仓库级测试基建重构
- 大范围 fixture 标准化或全仓库 flaky 治理
- 测试命令、质量门禁、分层约定或文档体系化整理
- 与当前高风险能力无关的覆盖率导向补测
- 大规模 CI/CD 平台建设或外部测试基础设施改造
- 用实现细节断言替代面向可观察行为的测试契约

## Done Criteria

- 系统 MUST 为 service runtime 和 service HTTP exposure 提供覆盖 happy path、配置校验失败、鉴权边界和错误传播的自动化回归测试
- 系统 MUST 为 SDK、HTTP 和 JSON-RPC 的关键公共行为提供跨接口一致性验证，避免同一能力在不同入口出现可观察漂移
- 系统 MUST 为 workflow 的暂停恢复、checkpoint 恢复、重试耗尽和审计事件提供稳定的自动化回归测试
- 每个 planned change MUST 能明确指向一个或多个现有 spec 区域及其可观察行为，而不是抽象的覆盖率目标
- 新增测试 MUST 以回答“已有行为是否会回归”为主要目标，而不是顺带整理测试体系
- 新增测试 MUST 保持独立性，不依赖共享可变状态，且优先使用项目自有真实依赖而非 mocks
- 测试补强相关变更 MUST 通过明确的 lint 或 validation 命令与 unit/integration test 命令验证

## Planned Changes
- `service-runtime-regression-tests` - Declared: complete - 补强 service runtime、service HTTP exposure、启动配置与错误传播的回归测试，提升真实部署路径的回归可发现性
- `workflow-recovery-regression-tests` - Declared: planned - 补强 workflow 的暂停恢复、checkpoint、retry exhaustion 和审计事件回归测试，提升复杂状态机场景的稳定性信心
- `cross-interface-consistency-tests` - Declared: planned - 补强 SDK、HTTP、JSON-RPC 三层接口的一致性验证，降低多入口行为漂移风险

## Dependencies

- M16 集成与发布（已有三层接口一致性目标与集成验证经验）
- M36 Lealone 原生服务应用运行时（提供 service runtime 与 service HTTP 暴露基础能力）
- M37 企业级工作流运行与治理（提供 workflow 生命周期、恢复与审计基础能力）

## Risks

- 若测试范围失焦，里程碑可能退化为宽泛的“补覆盖率”工作，难以形成明确完成标准
- 若跨接口一致性断言设计不当，可能把各接口允许存在的合理差异误判为缺陷
- workflow 和 service runtime 测试若过度依赖脆弱时序，容易引入 flaky tests 并削弱回归信号可信度

## Status
- Declared: proposed

## Notes

- 本里程碑是跨能力的质量补强里程碑，不重新定义 M16、M36、M37 的功能范围，而是为这些既有能力补上更强的回归保护
- 规划重点不是追求全局覆盖率数字，而是优先覆盖最可能造成接口漂移、运行时回归和复杂状态错误的测试空白
- 判定原则：如果目标是防止某个已有外部行为回归，归入 M39；如果目标是让很多测试更容易写、更稳定或更可复用，则应归入 M40
- 若需要执行 Maven 相关验证命令，本里程碑中的测试与验证工作统一使用 `mvnd`，不使用 `mvn`
- 推荐推进顺序：先执行 `service-runtime-regression-tests`，再执行 `workflow-recovery-regression-tests`，最后执行 `cross-interface-consistency-tests`；完成 M39 后再进入 M40 的测试体系治理工作

