# llm-config-secret-redaction

## What

在配置持久化、事件元数据、日志和异常消息等链路中阻断 LLM 配置的 secret 明文泄露，确保 API key 等敏感值在任何可观测输出中以脱敏形式呈现。

## Why

M33 的前两个 change（`llm-config-vault-integration` 和 `set-llm-permission-guard`）已完成 Vault 引用解析和权限校验，但 `LlmConfig` record 的自动 `toString()` 会原样输出 `apiKey` 字段。当前虽然没有显式调用 `toString()` 的代码路径，但 Java record 的隐式行为意味着任何未来的日志、异常消息、调试输出都可能无意泄露 secret。需要在 secret 可能流经的所有输出链路中建立防御性脱敏层，而不是依赖"没人调用 toString"的隐性假设。

## Scope

- `LlmConfig` record 的 `toString()` 输出必须脱敏 `apiKey` 字段
- `LlmConfig` 构造失败时的异常消息不得包含 `apiKey` 明文
- `DefaultLlmProviderRegistry` 中涉及 provider 认证配置的异常消息不得泄露 resolved secret
- `VaultResolver` 解析失败时的异常消息不得包含已解析的 secret 明文
- 事件元数据（`LLM_CONFIG_CHANGED` 及其他涉及 LLM 配置的事件）的防御性校验：确保 secret 值不出现在 metadata 中
- 配置持久化层的防御性校验：确保 secret 值不被写入数据库
- 自动化测试覆盖上述脱敏场景

## Unchanged Behavior

- `LlmConfig.apiKey()` 方法调用仍返回实际密钥值（运行时认证需要）
- Provider 创建和 HTTP 请求行为不变
- Vault 引用解析逻辑不变
- 权限校验行为不变
- `LlmConfigSnapshot` 的现有行为不变（已设计为不含 secret）
