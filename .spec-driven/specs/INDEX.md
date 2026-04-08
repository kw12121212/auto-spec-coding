# Specs Index

- [tool-interface.md](tool-interface.md) - Tool, ToolInput, ToolResult, ToolContext, ToolParameter
- [agent-interface.md](agent-interface.md) - Agent, AgentState, AgentContext, Message, Conversation
- [event-system.md](event-system.md) - Event, EventType, EventBus
- [permission-interface.md](permission-interface.md) - PermissionProvider, Permission, PermissionContext
- [config-loader.md](config-loader.md) - ConfigLoader, Config, ConfigException
- [bash-tool.md](bash-tool.md) - BashTool, shell command execution with timeout and permission checks
- [file-ops-tools.md](file-ops-tools.md) - ReadTool, WriteTool, EditTool — file read/write/edit with permission checks
- [tool-grep.md](tool-grep.md) - GrepTool — content search with regex, glob filtering, and multiple output modes
- [tool-glob.md](tool-glob.md) - GlobTool — file pattern matching with glob, sorted by modification time
- [llm-provider.md](llm-provider.md) - LlmProvider, LlmConfig, LlmRequest, LlmResponse, LlmUsage, ToolSchema, LlmStreamCallback, LlmProviderRegistry, DefaultLlmProviderRegistry, SkillRoute, LlmProviderFactory
- [task-registry.md](task-registry.md) - Task, TaskStatus, TaskStore, LealoneTaskStore
- [team-registry.md](team-registry.md) - Team, TeamStatus, TeamMember, TeamRole, TeamStore, LealoneTeamStore
- [cron-registry.md](cron-registry.md) - CronEntry, CronStatus, CronExpression, CronStore, LealoneCronStore
- [tool-lsp.md](tool-lsp.md) - LspTool, LspClient — LSP client tool for code intelligence (diagnostics, hover, definition, references, document symbols)
- [tool-mcp.md](tool-mcp.md) - McpTransport, McpClient, McpServer, McpToolAdapter, McpClientRegistry — MCP protocol client/server, tool discovery and adaptation
- [secret-vault.md](secret-vault.md) - SecretVault, VaultEntry, VaultResolver, VaultMasterKey, VaultException — encrypted secret storage interface and config resolution
- [sdk-public-api.md](sdk-public-api.md) - SpecDriven, SdkBuilder, SdkAgent, SdkConfig, SdkException, SdkConfigException, SdkVaultException, SdkLlmException, SdkToolException, SdkPermissionException, SdkEventListener — public SDK facade with builder pattern, auto-assembly, typed error handling, and event callbacks
