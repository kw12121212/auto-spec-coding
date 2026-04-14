# M18 - 密钥保险库

## Goal

实现基于 Lealone DB 的密钥保险库系统，使用单一 MASTER_KEY 环境变量解锁所有敏感凭证（API key、token 等），为 LLM provider 和其他需要密钥的模块提供统一的密钥管理基础设施。

## In Scope

- SecretVault 接口定义（get、set、delete、list 密钥）
- LealoneVault 实现：AES-256-GCM 加密存储，Lealone 表持久化
- 单一 MASTER_KEY 环境变量解锁机制（`SPEC_DRIVEN_MASTER_KEY`）
- 密钥生命周期管理（创建、轮换、删除）
- 与 config 加载链集成：`vault 解密 → 填入 config map → 模块消费`

## Out of Scope

- 密钥分发/共享协议（多节点同步）
- 密钥自动轮换策略
- 具体 provider 的密钥使用逻辑（M05 LlmConfig 只消费明文）

## Done Criteria

- 可通过 `SecretVault.get("openai_key")` 获取解密后的密钥
- 密钥在 Lealone DB 中以 AES-256-GCM 密文存储，无 MASTER_KEY 无法解密
- MASTER_KEY 仅通过环境变量 `SPEC_DRIVEN_MASTER_KEY` 传入
- config map 中的 `vault:key_name` 引用在加载时自动 resolve 为明文
- 密钥的 CRUD 操作均有审计日志
- 有单元测试验证加密/解密/resolve 链路

## Planned Changes
- `secret-vault-interface` - Declared: complete - SecretVault 接口定义与 VaultResolver（config map 中的 vault: 引用自动 resolve）
- `lealone-vault-impl` - Declared: complete - LealoneVault 实现：AES-256-GCM 加密、Lealone 表存储、MASTER_KEY 环境变量解锁、审计日志
- `vault-config-integration` - Declared: complete - 将 VaultResolver 集成到 AgentContext.config() 加载链，使所有模块透明使用 vault 密钥

## Dependencies

- M01 核心接口（配置加载基础设施）
- Lealone 数据库模块（lealone-db, lealone-sql）用于密钥表和审计日志
- M05 LLM 后端（vault 的主要消费方，但 vault 可独立开发先用 mock 验证）

## Risks

- MASTER_KEY 泄露等同于所有密钥泄露，需文档强调环境变量安全
- AES-256-GCM 的 nonce 管理需正确实现以避免重用攻击
- vault 表的访问权限需与 Lealone 权限模型（M06）协调

## Status

- Declared: complete

## Notes

- 设计灵感：HashiCorp Vault 的简化版，但无需独立服务进程，直接嵌入 Lealone DB
- LlmConfig 保持简单——只接明文字符串，不感知 vault 存在
- 解密发生在 config 加载链的最早层：vault resolve → config map → LlmConfig.fromMap()
- 与 M06（权限模型）互补：M06 管行为权限，M18 管密钥安全


