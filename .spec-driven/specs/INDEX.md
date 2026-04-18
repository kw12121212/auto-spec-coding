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
- [llm-runtime-config.md](llm/llm-runtime-config.md) - Runtime LLM Config
- [llm-cache.md](llm/llm-cache.md) - LlmCache, LealoneLlmCache, CacheKeyGenerator, CachingLlmClient, UsageRecord
- [context-relevance-scorer.md](llm/context-relevance-scorer.md) - ContextRelevanceScorer, KeywordContextRelevanceScorer, keyword-based tool-result relevance scoring
- [context-retention-policy.md](llm/context-retention-policy.md) - ContextRetentionPolicy, retention decisions, and mandatory context reasons
- [tool-result-filter.md](llm/tool-result-filter.md) - ToolResultFilter, DefaultToolResultFilter, ToolResultFilterInput
- [conversation-summarizer.md](llm/conversation-summarizer.md) - ConversationSummarizer, DefaultConversationSummarizer, ConversationSummary, ConversationSummarizerInput
- [smart-context-injector.md](llm/smart-context-injector.md) - SmartContextInjector, SmartContextInjectorConfig, smart context optimization decorator

## API (API 层)

- [http-api.md](api/http-api.md) - HttpApiServlet, AuthFilter, RateLimitFilter — HTTP REST API
- [http-e2e-tests.md](api/http-e2e-tests.md) - HTTP REST API E2E integration tests
- [service-http-exposure.md](api/service-http-exposure.md) - Application service HTTP exposure for Lealone Service invocation
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

## Interactive (交互式会话)

- [interactive-session.md](interactive/interactive-session.md) - InteractiveSession, InteractiveSessionState, InMemoryInteractiveSession — interactive session contract and lifecycle

## Question (问题/问答系统)

- [question-resolution.md](question/question-resolution.md) - Question, Answer, DeliveryChannel, ReplyCollector, QuestionDeliveryService, Mobile channels

## Registry (注册中心)

- [task-registry.md](registry/task-registry.md) - Task, TaskStatus, TaskStore, LealoneTaskStore
- [team-registry.md](registry/team-registry.md) - Team, TeamStatus, TeamMember, TeamRole, TeamStore, LealoneTeamStore
- [cron-registry.md](registry/cron-registry.md) - CronEntry, CronStatus, CronExpression, CronStore, LealoneCronStore

## Config (配置管理)

- [config-loader.md](config/config-loader.md) - ConfigLoader, Config, ConfigException
- [environment-profile.md](config/environment-profile.md) - Project YAML environment profiles and effective profile selection

## Skill (Skill 系统)

- [skill-auto-discovery.md](skill/skill-auto-discovery.md) - SkillAutoDiscovery, DiscoveryResult, SkillDiscoveryError
- [skill-cli-java.md](skill/skill-cli-java.md) - Java-native shared spec-driven CLI
- [skill-executor-plugin.md](skill/skill-executor-plugin.md) - SkillServiceExecutorFactory, SkillServiceExecutor
- [skill-instructions-store.md](skill/skill-instructions-store.md) - SkillInstructionStore, FileSystemInstructionStore
- [class-cache-manager.md](skill/class-cache-manager.md) - ClassCacheManager, ClassCacheException, LealoneClassCacheManager — disk-backed compiled skill class cache
- [skill-source-compiler.md](skill/skill-source-compiler.md) - SkillSourceCompiler, SkillCompilationResult, SkillCompilationDiagnostic, LealoneSkillSourceCompiler
- [skill-sql-converter.md](skill/skill-sql-converter.md) - SkillFrontmatter, SkillMarkdownParser, SkillSqlConverter
- [skill-hot-loader.md](skill/skill-hot-loader.md) - SkillHotLoader, SkillHotLoaderException, SkillLoadResult, LealoneSkillHotLoader — runtime registry of compiled skill ClassLoaders with hot-replace

## Platform (平台基础设施)

- [lealone-platform.md](platform/lealone-platform.md) - LealonePlatform and typed platform capability access for DB, runtime LLM, compiler/hot-load, and interactive session
- [sandlock-runner.md](platform/sandlock-runner.md) - Sandlock-backed profile command execution and explicit launch diagnostics

## SDK (SDK 公共 API)

- [sdk-public-api.md](sdk/sdk-public-api.md) - SpecDriven, SdkBuilder, SdkAgent, SdkConfig, SdkException
- [autonomous-loop.md](sdk/autonomous-loop.md) - LoopDriver, LoopScheduler, LoopConfig, LoopState, LoopIteration
- [go-client-sdk.md](sdk/go-client-sdk.md) - Go HTTP client SDK for Java backend REST API
- [ts-client-sdk.md](sdk/ts-client-sdk.md) - TypeScript HTTP client SDK for Java backend REST API

## Workflow (工作流)

- [workflow-runtime.md](workflow/workflow-runtime.md) - Workflow declaration, lifecycle state, result, and audit visibility contract
- [workflow-step-composition.md](workflow/workflow-step-composition.md) - WorkflowStep, WorkflowStepExecutor, WorkflowStepResult — ordered step composition and step audit events

## ORM (ORM 采用准则)

- [orm-adoption.md](orm/orm-adoption.md) - ORM migration admission criteria, escape-hatch rules, and coexistence contract

## Testing (测试基础设施)

- [test-fixtures.md](testing/test-fixtures.md) - CapturingEventBus, LealoneTestDb — shared test fixture helpers in testsupport package
- [test-quality-gates.md](testing/test-quality-gates.md) - standard lint, unit test, integration test commands and three-layer test convention

## Release (发布准备)

- [release-preparation.md](release/release-preparation.md) - Repository release overview and Maven release metadata
- [service-runtime-packaging.md](release/service-runtime-packaging.md) - Java service application runtime packaging and startup entrypoint
