# M34 - Skill 动态编译治理与审计

## Goal

在 M30 的动态编译与热加载基础链路之上，补齐默认关闭、权限门控、来源信任校验和审计能力，使该能力仅能在受信管理员工作流下启用，并避免未经授权的动态代码进入激活路径。

## In Scope

- 动态编译与热加载能力的默认关闭策略与显式启用开关
- 编译、加载、卸载、替换入口的权限校验
- 受信来源校验与激活前门控，阻止未经授权的源码进入生效路径
- compile/load/unload/replace 操作的最小审计记录

## Out of Scope

- 对不受信代码提供完整安全沙箱
- 多租户插件市场或公网代码上传入口
- 代码签名基础设施或远程发布系统
- 非 Java skill 的治理模型

## Done Criteria

- 动态编译与热加载能力 MUST 默认关闭，且仅允许受信管理员或本地受信工作流启用
- 未授权或来源不受信的动态编译请求 MUST 被拒绝，且不得写入激活路径
- 所有 compile/load/unload/replace 操作 MUST 记录审计信息，至少包含操作者、时间、skill 标识和结果
- 有自动化测试覆盖默认关闭、权限拒绝、受信来源校验和审计记录场景

## Planned Changes
- `hot-load-default-disabled` - Declared: complete - 为动态编译与热加载增加默认关闭策略和显式启用开关，避免能力被意外暴露
- `hot-load-permission-guard` - Declared: complete - 为编译、加载、卸载和替换入口增加权限校验，限制仅受信管理员可操作
- `trusted-source-activation-gate` - Declared: complete - 在激活前增加受信来源校验与阻断逻辑，防止未经授权的源码进入生效路径
- `hot-load-audit-log` - Declared: complete - 为 compile/load/unload/replace 操作增加最小审计记录，支持追踪与回溯

## Dependencies

- M30 动态编译与 Skill 热加载（提供编译与热替换基础链路）
- M06 权限模型与执行钩子（权限校验）
- M01 核心接口（事件与审计基础）

## Risks

- 默认关闭策略若被绕过，仍可能在非预期环境暴露动态代码入口
- 受信来源规则若过于宽松，未经授权的代码仍可能进入激活路径
- 审计覆盖不完整会削弱后续问题追踪和责任界定能力

## Status
- Declared: complete

## Notes

- 本 milestone 目标是治理而不是沙箱；即使完成后，也不意味着系统可以安全执行任意不受信代码
- 若未来需要多租户或 plugin market，应另开更严格的安全与隔离专题 milestone
