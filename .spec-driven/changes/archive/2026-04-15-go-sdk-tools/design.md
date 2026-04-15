# Design: go-sdk-tools

## Approach

采用 callback-backed remote tool 设计。Go SDK 调用方在本进程内实现工具 handler，并把该 handler 挂载到自己的 `net/http` server；随后通过 Go SDK tools facade 向 Java 后端注册工具名称、描述、参数 schema 和 callback URL。Java 后端把该注册项作为一种远程 Tool 暴露给既有 SDK 工具列表和 agent orchestration。

后端 agent run 中出现该工具调用时，Java 后端按已有工具执行流程处理：工具仍由 orchestrator 调用，hook/error/conversation 语义保持一致；remote tool 的执行动作只是向注册 callback URL 发送 invocation payload，并把 callback response 映射为 `ToolResult.Success` 或 `ToolResult.Error`。

Go SDK 新增的工具层保持在现有 `go-sdk/specdriven` package 内：

- `Tools` facade 复用现有 `Client` 的 HTTP/auth/retry/error handling
- tool registration 模型复用现有 `ToolInfo`/parameter JSON field 约定
- callback handler 使用 Go 标准库 `net/http`
- local handler registry 只负责本进程 callback dispatch，不尝试启动或管理 HTTP server

Java 侧新增最小 HTTP contract：

- `POST /api/v1/tools/register` 注册 callback-backed remote tool
- `GET /api/v1/tools` 合并已有 SDK tools 和 remote tools
- agent run 中可调用 remote tools，callback failure 作为 tool error 反馈给 LLM self-repair

## Key Decisions

- 选择 callback-backed remote tool，而不是直接上传 Go 代码或在 Java 端嵌入 Go runtime。这样保持 Go SDK 只是客户端库，Java 后端仍是唯一 agent runtime。
- 不新增通用直接执行后端工具的 HTTP endpoint。工具调用应继续经过 agent orchestration，避免绕过既有 permission hook、tool result feedback 和 conversation 语义。
- 远程注册不得覆盖已有内置工具名称。已有 Java SDK tools 的行为必须稳定，remote tool registration 只能增加新的可用工具或替换先前同名 remote registration。
- Go SDK 不托管 callback server。调用方负责 server lifecycle、TLS、监听地址和网络可达性，SDK 只提供 handler 和 registration helper。
- Callback payload 使用 JSON object parameters 和 success/error response。该模型与现有 `ToolInput`/`ToolResult` 可直接对应，也方便 Go 调用方实现。
- 测试以 unit tests 为主。Java 使用 servlet/tool fake 覆盖 route 和 callback behavior；Go 使用 `httptest` 覆盖 client registration 和 callback handler。真实跨进程集成留给 `go-sdk-tests`。

## Alternatives Considered

- 只封装现有 `GET /tools`：实现简单，但无法满足 roadmap 中的工具注册与调用能力。
- 新增 `POST /tools/invoke` 直接执行任意后端工具：调用方便，但会形成绕过 agent/tool orchestration 的高风险入口，暂不采用。
- 在每次 `Agent.Run` 请求中携带临时工具定义：会扩大 run request 契约，并使工具 lifecycle 与 agent run 强耦合，暂不采用。
- 让 Go SDK 自动启动 callback HTTP server：会把端口、TLS、部署和生命周期管理引入客户端库，超出本 change 范围。
- 通过 JSON-RPC 实现工具注册：M20 明确 Go SDK 仅走 HTTP REST API，暂不采用。
