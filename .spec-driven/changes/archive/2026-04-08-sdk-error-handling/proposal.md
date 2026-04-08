# sdk-error-handling

## What

为 SDK 引入类型化的异常层次结构和 `retryable` 标记，并改造现有 SDK 代码（`SdkBuilder`、`SdkAgent`）使用新的子类异常替代通用 `SdkException`。

## Why

当前所有 SDK 错误都抛出通用 `SdkException`，调用方无法按错误类型进行区分处理（如配置错误 vs LLM 超时）。M12 的目标是提供 "统一错误处理模式"，但现有实现只有一种异常类型，无法满足下游 M13（JSON-RPC 自动重试）和 M14（HTTP 错误码映射）的需求。

## Scope

**In Scope:**
- 定义 `SdkException` 子类层次：`SdkConfigException`、`SdkLlmException`、`SdkToolException`、`SdkPermissionException`、`SdkVaultException`
- 所有子类支持 `isRetryable()` 标记
- 改造 `SdkBuilder.build()` 中的 `ConfigException` → `SdkConfigException`，vault 错误 → `SdkVaultException`
- 改造 `SdkAgent.run()` 中的 LLM 错误 → `SdkLlmException`，工具错误 → `SdkToolException`
- 更新 `SdkExceptionTest` 并为每个子类添加测试

**Out of Scope:**
- 不改变 `SdkException` 作为 `RuntimeException` 的基类性质
- 不修改底层 `ConfigException`、`VaultException` 等内部异常类型
- 不添加 `retryAfterMs()` 等扩展元数据（可后续迭代）

## Unchanged Behavior

- `SdkException` 仍然是所有 SDK 异常的基类，现有 `catch (SdkException)` 代码不受影响
- 现有构造函数签名保持不变，子类通过新增构造函数实现
- 事件系统（`ERROR` 事件）的行为不变
