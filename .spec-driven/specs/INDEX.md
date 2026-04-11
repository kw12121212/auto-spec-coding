# Specs Index

## Core (核心接口)

- [agent-interface.md](core/agent-interface.md) - Agent, AgentState, AgentContext, Message, Conversation
- [tool-interface.md](core/tool-interface.md) - Tool, ToolInput, ToolResult, ToolContext, ToolParameter

## Tools (工具实现)

- [bash-tool.md](tools/bash-tool.md) - BashTool, shell command execution with timeout and permission checks
- [file-ops-tools.md](tools/file-ops-tools.md) - ReadTool, WriteTool, EditTool — file read/write/edit with permission checks
- [tool-grep.md](tools/tool-grep.md) - GrepTool — content search with regex, glob filtering, and multiple output modes
- [tool-glob.md](tools/tool-glob.md) - GlobTool — file pattern matching with glob, sorted by modification time
- [tool-lsp.md](tools/tool-lsp.md) - LspTool, LspClient — LSP client tool for code intelligence
- [tool-mcp.md](tools/tool-mcp.md) - McpTransport, McpClient, McpServer, McpToolAdapter, McpClientRegistry — MCP protocol
- [background-tool-interface.md](tools/background-tool-interface.md) - BackgroundTool, BackgroundProcessHandle, ProcessState, ProcessOutput
- [builtin-tool-manager.md](tools/builtin-tool-manager.md) - BuiltinTool, BuiltinToolManager, DefaultBuiltinToolManager
- [tool-execution-cache.md](tools/tool-execution-cache.md) - ToolCache, CacheEntry, ToolCacheKey, LealoneToolCache, CachingTool

## LLM (LLM 提供者)

- [llm-provider.md](llm/llm-provider.md) - LlmProvider, LlmConfig, LlmRequest, LlmResponse, LlmUsage, ToolSchema, LlmStreamCallback, LlmProviderRegistry, DefaultLlmProviderRegistry, SkillRoute, LlmProviderFactory
- [llm-cache.md](llm/llm-cache.md) - LlmCache, LealoneLlmCache, CacheKeyGenerator, CachingLlmClient, UsageRecord
- [context-relevance-scorer.md](llm/context-relevance-scorer.md) - ContextRelevanceScorer, KeywordContextRelevanceScorer, keyword-based tool-result relevance scoring

## API (API 层)

- [http-api.md](api/http-api.md) - HttpApiServlet, AuthFilter, RateLimitFilter — HTTP REST API
- [http-e2e-tests.md](api/http-e2e-tests.md) - HTTP REST API E2E integration tests
- [jsonrpc-protocol.md](api/jsonrpc-protocol.md) - JsonRpcRequest, JsonRpcResponse, JsonRpcNotification, JsonRpcError, JsonRpcCodec
- [jsonrpc-handlers.md](api/jsonrpc-handlers.md) - JsonRpcDispatcher — request routing and SDK operation mapping
- [jsonrpc-transport.md](api/jsonrpc-transport.md) - JsonRpcMessageHandler, JsonRpcTransport, StdioTransport
- [jsonrpc-e2e-tests.md](api/jsonrpc-e2e-tests.md) - JSON-RPC E2E integration tests

## Event (事件系统)

- [event-system.md](event/event-system.md) - Event, EventType, EventBus, AuditLogStore, LealoneAuditLogStore

## Permission (权限系统)

- [permission-interface.md](permission/permission-interface.md) - PermissionProvider, Permission, PermissionContext, PolicyStore, LealonePolicyStore

## Vault (密钥保管库)

- [secret-vault.md](vault/secret-vault.md) - SecretVault, VaultEntry, VaultResolver, VaultFactory, LealoneVault

## Question (问题/问答系统)

- [question-resolution.md](question/question-resolution.md) - Question, Answer, DeliveryChannel, ReplyCollector, QuestionDeliveryService, Mobile channels

## Registry (注册中心)

- [task-registry.md](registry/task-registry.md) - Task, TaskStatus, TaskStore, LealoneTaskStore
- [team-registry.md](registry/team-registry.md) - Team, TeamStatus, TeamMember, TeamRole, TeamStore, LealoneTeamStore
- [cron-registry.md](registry/cron-registry.md) - CronEntry, CronStatus, CronExpression, CronStore, LealoneCronStore

## Config (配置管理)

- [config-loader.md](config/config-loader.md) - ConfigLoader, Config, ConfigException

## Skill (Skill 系统)

- [skill-auto-discovery.md](skill/skill-auto-discovery.md) - SkillAutoDiscovery, DiscoveryResult, SkillDiscoveryError
- [skill-cli-java.md](skill/skill-cli-java.md) - Java-native shared spec-driven CLI
- [skill-executor-plugin.md](skill/skill-executor-plugin.md) - SkillServiceExecutorFactory, SkillServiceExecutor
- [skill-instructions-store.md](skill/skill-instructions-store.md) - SkillInstructionStore, FileSystemInstructionStore
- [skill-sql-converter.md](skill/skill-sql-converter.md) - SkillFrontmatter, SkillMarkdownParser, SkillSqlConverter

## SDK (SDK 公共 API)

- [sdk-public-api.md](sdk/sdk-public-api.md) - SpecDriven, SdkBuilder, SdkAgent, SdkConfig, SdkException
- [autonomous-loop.md](sdk/autonomous-loop.md) - LoopDriver, LoopScheduler, LoopConfig, LoopState, LoopIteration

## Release (发布准备)

- [release-preparation.md](release/release-preparation.md) - Repository release overview and Maven release metadata
