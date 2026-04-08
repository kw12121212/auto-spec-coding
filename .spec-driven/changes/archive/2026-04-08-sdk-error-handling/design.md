# Design: sdk-error-handling

## Approach

在 `org.specdriven.sdk` 包下扩展异常层次。`SdkException` 基类增加 `isRetryable()` 方法（默认 false），每个子类根据领域语义决定默认 retryable 值，并允许通过构造参数覆盖。

异常层次：
```
SdkException (base, isRetryable=false)
├── SdkConfigException     (配置加载/解析错误, retryable=false)
├── SdkVaultException      (密钥保险库错误, retryable=false)
├── SdkLlmException        (LLM provider 调用错误, retryable=true 默认)
├── SdkToolException       (工具执行错误, retryable=false 默认)
└── SdkPermissionException (权限拒绝, retryable=false)
```

改造策略：
- `SdkBuilder.build()`: 捕获 `ConfigException` → 抛 `SdkConfigException`；捕获 vault 相关异常 → 抛 `SdkVaultException`
- `SdkAgent.run()`: LLM 调用失败 → `SdkLlmException`；工具执行失败 → `SdkToolException`；其他 → 通用 `SdkException`

## Key Decisions

1. **子类继承而非 flat ErrorCode** — Java 惯用模式，调用方可 `catch (SdkLlmException e)` 分类型处理，IDE 友好
2. **retryable 作为字段而非方法** — 通过构造参数传入，子类提供领域默认值，避免硬编码
3. **同步改造现有代码** — 否则新异常类型无人使用，M12 错误处理目标无法真正达成
4. **基类 SdkException 保持 binary compatible** — 新增 `isRetryable()` 方法不影响已有构造函数和子类

## Alternatives Considered

- **Flat SdkException + ErrorCode 枚举** — 更像 Go/Rust 风格，但 Java 消费者更习惯类型化 catch
- **仅定义类型不改造现有代码** — 无实际价值，需后续 change 才能集成
- **retryable + retryAfterMs** — 当前场景不需要精确重试间隔，YAGNI
