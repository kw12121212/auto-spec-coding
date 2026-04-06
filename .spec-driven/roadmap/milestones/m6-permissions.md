# M6 - 权限模型与执行钩子

## Goal

实现权限模型定义和执行钩子机制，为所有工具调用和 agent 操作提供安全基础设施。

## In Scope

- 权限模型定义（角色、策略、规则）
- 权限检查执行钩子（工具调用前拦截）
- 权限决策结果（允许/拒绝/需确认）
- 默认权限策略实现

## Out of Scope

- 具体权限策略在各接口层的定制（由 M11-M13 各层自行扩展）
- 注册表功能（M7、M8）

## Done Criteria

- 权限检查可在工具调用链中被正确触发
- 权限拒绝场景返回结构化错误
- 有单元测试覆盖权限允许、拒绝、需确认三种场景
- 默认策略可覆盖 bash 和文件操作的基本安全约束

## Planned Changes

- `permissions-model` - Declared: planned - 权限模型定义与策略接口实现
- `permissions-hooks` - Declared: planned - 权限检查执行钩子与工具调用拦截实现

## Dependencies

- M1 核心接口（PermissionProvider）
- M4 Agent 生命周期（权限检查注入到编排循环）

## Risks

- 权限模型设计过紧会限制后续接口层的灵活性
- 默认策略的覆盖面需要与 spec-coding-sdk 的安全行为对齐

## Status

- Declared: proposed

## Notes

- 权限模型需同时支持 SDK 嵌入场景（自动授权）和服务化场景（需确认）
