# Questions: llm-provider-interface

## Open

<!-- No open questions -->

## Resolved

- [x] Q: LlmConfig 的 apiKey 是否需要支持从环境变量或文件读取？
  Context: 当前 AgentContext.config() 返回 Map<String,String>，apiKey 可能不适合直接放在配置 map 中。如果需要额外支持，会影响 LlmConfig 的构建方式。
  A: LlmConfig 只接明文字符串，不内置环境变量/文件解析。密钥管理由独立的 SecretVault 模块负责（AES-256-GCM + Lealone DB 存储，单一 MASTER_KEY 环境变量解锁所有密钥），在 config 加载链中比 LlmConfig 更早一层。
