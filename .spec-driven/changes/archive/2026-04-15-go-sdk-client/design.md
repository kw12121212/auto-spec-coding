# Design: go-sdk-client

## Approach

在仓库内新增 `go-sdk` 子目录作为独立 Go module，公开 package 名称使用 `specdriven`。该 module 提供一个底层 `Client`，负责构造 HTTP 请求、编码 JSON body、解析 JSON response、处理认证 header、应用超时与重试策略，并把 HTTP/API 错误转成 Go 调用方可判断的 typed error。

Client 首期只按现有 HTTP REST API 暴露低层方法，不提供业务级 facade。方法命名围绕 endpoint 行为：

- `Health(ctx)`
- `ListTools(ctx)`
- `RunAgent(ctx, request)`
- `StopAgent(ctx, agentID)`
- `GetAgentState(ctx, agentID)`

测试使用 Go 标准库 `httptest` 搭建 fake HTTP server，验证请求路径、方法、header、JSON body、响应解析、错误映射和重试次数。这样不依赖真实 Java 后端，也不把该 change 扩大成跨语言集成测试。

## Key Decisions

- Go module 放在 `go-sdk/` 下，而不是仓库根目录。根目录仍是 Maven Java 项目，子目录 module 能避免 Maven/Go 构建互相污染。
- 首期模块路径使用 `github.com/kw12121212/auto-spec-coding/go-sdk`。该路径可从现有 `pom.xml` SCM URL 推导，并允许调用方通过 Go module 机制直接引用子模块。
- 只实现 HTTP REST API 传输。M20 明确 Go SDK 仅走 HTTP REST API，不覆盖 JSON-RPC。
- 认证同时支持 Bearer token 和 `X-API-Key`。现有 HTTP E2E spec 已声明两个认证 header 都有效，Go client 应允许调用方选择其中一种。
- 重试只覆盖网络错误、HTTP 429 和 5xx，不重试 4xx 参数/认证/权限错误。这样与 HTTP 状态语义一致，也避免 SDK 隐藏调用方错误。
- 错误类型保留 HTTP status、API error code、message 和 retryable 标记。调用方可以按错误类型、状态码或 `Retryable()` 判断处理方式。

## Alternatives Considered

- 把 Go SDK 放在仓库根 module：会与现有 Maven 项目混合，增加构建和发布噪音，暂不采用。
- 直接实现高层 `Agent` facade：这会跨入 `go-sdk-agent` 的范围，导致首个 change 过大，暂不采用。
- 同时支持 JSON-RPC：M20 已明确 Go SDK 仅走 HTTP REST API，暂不采用。
- 首期连接真实 Java 后端做集成测试：该能力属于 `go-sdk-tests`，本 change 使用 `httptest` 覆盖 client 行为。
