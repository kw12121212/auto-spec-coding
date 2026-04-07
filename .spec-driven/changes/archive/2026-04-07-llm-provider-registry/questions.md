# Questions: llm-provider-registry

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `fromConfig` eagerly instantiate providers at config-load time, or lazily on first `provider()` call?
  Context: Eager instantiation fails fast on misconfigured providers but may waste resources if some providers are never used. Lazy defers errors to first use.
  A: 立即创建 — 配置阶段就暴露错误，避免运行时才发现问题

- [x] Q: Should `register()` allow replacing an existing provider with the same name (overwrite), or always reject duplicates?
  Context: Overwrite is convenient for hot-reload scenarios but risks leaking resources from the replaced provider. Reject-duplicate is safer for initial implementation.
  A: 拒绝重复 — 更安全；如需替换功能，后续可加 `replace()` 方法

- [x] Q: Should `remove()` automatically close the removed provider, or just unregister it?
  Context: Auto-close prevents resource leaks but may surprise callers who still hold a client reference from that provider.
  A: 移除时自动关闭 — 防止资源泄漏
