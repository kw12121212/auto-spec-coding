# Design: llm-config-secret-redaction

## Approach

在 `LlmConfig` record 上覆盖 `toString()` 方法，将 `apiKey` 字段替换为固定脱敏占位符 `***`。同时审查所有使用 `LlmConfig` 或 provider 认证配置的异常路径，确保异常消息不包含明文 secret。

具体步骤：
1. **LlmConfig.toString() 覆盖**：显式定义 `toString()` 方法，输出 `apiKey=***` 而非实际值
2. **异常消息审查**：检查 `LlmConfig` compact constructor 和 `fromMap()` 的 `IllegalArgumentException` 消息，确保不直接引用传入的 apiKey 值
3. **VaultResolver 异常消息**：检查 `VaultException` 消息中是否包含已解析的 secret 值
4. **事件元数据防御性断言**：在 `DefaultLlmProviderRegistry` 发布 `LLM_CONFIG_CHANGED` 事件时添加断言或保护，确保 metadata map 不含 secret 值
5. **持久化防御性断言**：在 `LealoneRuntimeLlmConfigStore` 持久化时添加断言，确认 snapshot 不含 secret 字段

## Key Decisions

1. **在 record 层覆盖 toString() 而非引入包装类型**：Java record 的隐式 toString() 是最不可控的泄露向量。直接覆盖 record 方法是最小侵入的修复，不需要引入新的 `SensitiveString` 类型或改变 `apiKey` 的存储方式。

2. **脱敏占位符使用固定 `***`**：不泄露 key 长度信息，不使用部分遮掩（如 `sk-***123`），因为部分遮掩仍可能为攻击者提供信息。

3. **防御性断言而非运行时拦截**：对于事件和持久化链路，当前实现已经不包含 secret。添加断言可以在开发期捕获未来回归，而非引入运行时过滤开销。

## Alternatives Considered

1. **引入 SensitiveString 类型**：将 `apiKey` 字段类型从 `String` 改为自定义 `SensitiveString`，在 `toString()` 中自动脱敏。优点是类型系统层面保证；缺点是需要改动所有使用 `apiKey()` 的调用方，侵入性高，且 Provider 构造 HTTP 请求时仍需获取明文值。

2. **全局日志过滤器**：在日志框架层添加 pattern-based 过滤。缺点是依赖特定日志框架、可能遗漏非日志路径（如异常消息、事件 JSON），且正则匹配可能误伤非 secret 字符串。

3. **运行时 Proxy/拦截层**：在 Provider 创建链路中插入脱敏 proxy。过度工程化，增加不必要的间接层。
