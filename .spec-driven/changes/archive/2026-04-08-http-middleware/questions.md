# Questions: http-middleware

## Open

<!-- No open questions -->

## Resolved

- [x] Q: How should valid API keys be configured at runtime?
  Context: AuthFilter needs a set of valid keys. Options include filter init-params, SDK builder config, or environment variable.
  A: Filter init-params 优先，`API_KEYS` 环境变量作为 fallback。

- [x] Q: What are the default rate limit thresholds (requests-per-window and window duration)?
  Context: RateLimitFilter needs sensible defaults that balance usability and abuse prevention.
  A: 默认 100 requests / 60 seconds（每分钟 100 次），可通过 filter init-params 覆盖。
