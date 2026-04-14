# M33 - LLM 配置密钥引用与权限治理

## Goal

在 M28 的动态非敏感 LLM 配置基础上，补齐密钥引用、权限控制和审计治理能力，确保共享或生产环境下的 LLM 配置变更不会绕过 Vault 边界，也不会把 secret 明文带入普通配置持久化链路。

## In Scope

- 将 LLM 配置中的敏感字段接入 `SecretVault` / `VaultResolver`，通过 Vault 引用完成装配
- `SET LLM` 和其他运行时配置变更入口的权限校验，限制仅受信操作者可修改配置
- 配置持久化、事件和日志中的敏感字段脱敏与明文阻断
- LLM 配置变更的最小审计记录，包含操作者、时间、修改范围和结果

## Out of Scope

- 新增新的 secret 后端或替换现有 Vault 实现
- Web UI 级别的权限管理界面
- 跨进程或跨集群的统一密钥分发
- 密钥轮换流程本身（继续由 Vault 生命周期负责）

## Done Criteria

- LLM 配置中的敏感字段 MUST 通过 Vault 引用解析参与装配，普通配置持久化层 MUST NOT 保存 secret 明文
- 未授权的 `SET LLM` 或其他运行时配置修改请求 MUST 被拒绝，并返回可审计的失败原因
- 配置变更相关事件、日志和审计记录 MUST 对敏感字段做脱敏处理
- 授权成功的配置修改 MUST 记录审计信息，至少包含操作者、时间、修改范围和结果
- 有自动化测试覆盖 Vault 引用解析、权限拒绝、脱敏记录和授权修改场景

## Planned Changes
- `llm-config-vault-integration` - Declared: complete - 接入 SecretVault 和 VaultResolver，确保敏感字段通过 Vault 引用管理并参与运行时配置装配
- `set-llm-permission-guard` - Declared: complete - 为 SET LLM 与其他配置更新入口增加权限校验，限制仅受信操作者可修改运行时配置
- `llm-config-secret-redaction` - Declared: complete - 在配置持久化、事件和日志链路中阻断 secret 明文并输出脱敏内容
- `llm-config-change-audit` - Declared: planned - 为成功与失败的配置变更增加最小审计记录，支持后续追踪

## Dependencies

- M28 动态 LLM 配置与热切换（提供运行时配置骨架）
- M06 权限模型与执行钩子（权限校验）
- M18 密钥保险库（SecretVault / VaultResolver）
- M01 核心接口（事件和审计基础）

## Risks

- Vault 引用解析时机不当可能导致配置装配失败或读取到过期密钥
- 权限策略若配置过宽，可能仍允许非预期操作者修改共享配置
- 脱敏规则若实现不严谨，仍可能在日志或异常中泄露敏感信息

## Status
- Declared: proposed

## Notes

- 本 milestone 不替代 Vault，只负责把动态 LLM 配置接到既有 Vault 边界上
- 若项目仅在本地受信环境中使用，可先实现 M28；若要在共享或生产环境开放动态配置，应与本 milestone 配套规划



