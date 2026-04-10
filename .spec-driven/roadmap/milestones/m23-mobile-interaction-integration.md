# M23 - 配置化移动交互集成层

## Goal

在 M22 的 question-resolution 协议之上，内置多种移动交互渠道的集成能力，使集成者只需通过配置即可启用移动推送、人工回复回流和会话恢复，无需自行编写渠道适配代码。

## In Scope

- 移动交互渠道的统一配置模型与 provider registry
- 多种内置移动通知适配器，覆盖主流 push / webhook / 企业 IM 通道
- question payload 到渠道消息体的模板映射与字段裁剪
- 人工回复回流、签名校验、question correlation 与恢复执行
- 渠道级重试、送达结果、失败原因与审计观测

## Out of Scope

- 自定义移动 App 客户端开发
- 单一厂商专属高级能力的深度定制 UI
- 非移动渠道的通用通知编排平台
- 问题路由与 AI 自动回复决策本身（由 M22 定义）

## Done Criteria

- 集成者可仅通过配置启用至少两类移动交互渠道，无需编写业务代码
- 系统可把 `question`、`impact`、`recommendation` 映射为渠道消息并发送
- 人工可通过渠道回复，系统可完成签名校验、question 关联并恢复对应 agent 执行
- 渠道配置错误、送达失败、回调验签失败等场景有结构化错误与审计记录
- 有单元测试覆盖配置装配、消息发送、人工回复回流、重试与失败观测场景

## Planned Changes
- `mobile-channel-config-registry` - Declared: complete - 定义移动交互渠道配置模型、provider registry 与按名称装配机制
- `builtin-mobile-adapters` - Declared: complete - 提供多种内置移动交互适配器，覆盖 push / webhook / 企业 IM 等主流渠道
- `question-message-templating` - Declared: planned - 将 question payload 映射为渠道消息模板，支持字段裁剪、默认文案与安全脱敏
- `mobile-reply-callbacks` - Declared: planned - 实现人工回复回流、签名校验、question correlation 与 agent 恢复执行链路
- `mobile-delivery-observability` - Declared: planned - 提供渠道级重试、送达状态、失败原因、审计事件与运维观测能力

## Dependencies

- M22 交互问题解析与多通道回复（question payload、delivery mode、pending question 生命周期）
- M14 HTTP REST API（回调接收与渠道 webhook 集成）
- M18 密钥保险库（渠道密钥、签名 secret、token 管理）
- M12 Native Java SDK 层（面向集成者暴露配置入口）

## Risks

- 各渠道的签名校验、限流和回调格式差异较大，统一抽象过度会削弱可维护性
- “只靠配置即可用”容易诱导做过度通用化，需严格限制首期支持范围
- 渠道故障、消息延迟或重复投递会导致人工回复回流时序复杂
- 模板字段若裁剪不当，可能向移动端泄露不必要的敏感上下文

## Status

- Declared: proposed

## Notes

- 该里程碑是 M22 的集成层扩展，目标是让集成者优先通过配置接入，而不是自己重写通知桥接
- 首期 SHOULD 先支持两到三类代表性渠道，再通过 provider registry 扩展更多渠道
- 渠道选择、凭据与回调地址应全部支持配置化，并与 vault 集成管理敏感信息
- 如需支持具体厂商的深度能力，应在后续 change 中按渠道单独扩展，而不是在本里程碑一次性铺开


