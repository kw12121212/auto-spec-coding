# llm-provider.md

## ADDED Requirements

### Requirement: LlmProvider Interface
The system MUST define a `LlmProvider` interface that represents a configured LLM provider capable of creating `LlmClient` instances.

#### Scenario: Create client from provider
- GIVEN a `LlmProvider` instance configured with valid `LlmConfig`
- WHEN `createClient()` is called
- THEN it MUST return a `LlmClient` instance ready for use

#### Scenario: Provider lifecycle
- GIVEN a `LlmProvider` instance
- WHEN `close()` is called
- THEN the provider MUST release all associated resources

### Requirement: LlmConfig Record
The system MUST define an immutable `LlmConfig` record holding provider configuration.

#### Scenario: Build from map
- GIVEN a `Map<String, String>` with keys `baseUrl`, `apiKey`, `model`, `timeout`
- WHEN `LlmConfig.fromMap(map)` is called
- THEN it MUST return a `LlmConfig` with the mapped values and sensible defaults for missing keys

#### Scenario: Required fields
- GIVEN a `LlmConfig`
- THEN it MUST expose `baseUrl()` and `apiKey()` as non-null strings

### Requirement: LlmRequest Record
The system MUST define an immutable `LlmRequest` record encapsulating all parameters for an LLM call.

#### Scenario: Minimal request
- GIVEN a list of messages
- WHEN `LlmRequest.of(messages)` is called
- THEN it MUST return a request with default temperature, no tools, and no system prompt

#### Scenario: Full request with tools
- GIVEN messages, system prompt, tool schemas, temperature, and maxTokens
- WHEN a `LlmRequest` is constructed with all parameters
- THEN all fields MUST be accessible via accessor methods

### Requirement: Enhanced LlmClient Interface
The system MUST enhance the existing `LlmClient` interface with a new overload that accepts `LlmRequest`.

#### Scenario: Chat with request object
- GIVEN an `LlmClient` instance and an `LlmRequest` containing messages and tool definitions
- WHEN `chat(LlmRequest)` is called
- THEN it MUST return an `LlmResponse`

#### Scenario: Backward compatibility
- GIVEN the existing `chat(List<Message>)` method signature
- WHEN existing code calls it
- THEN it MUST continue to work without modification

### Requirement: Enhanced LlmResponse
The system MUST extend the existing `LlmResponse` sealed interface with usage and finish reason metadata.

#### Scenario: Text response with usage
- GIVEN an LLM returns a text completion
- WHEN the `TextResponse` is created
- THEN it MUST include `content()`, `usage()`, and `finishReason()`

#### Scenario: Tool call response with usage
- GIVEN an LLM returns tool call requests
- WHEN the `ToolCallResponse` is created
- THEN it MUST include `toolCalls()`, `usage()`, and `finishReason()`

### Requirement: LlmUsage Record
The system MUST define an immutable `LlmUsage` record for token usage statistics.

#### Scenario: Usage fields
- GIVEN an `LlmUsage` instance
- THEN it MUST expose `promptTokens()`, `completionTokens()`, and `totalTokens()` as non-negative integers

### Requirement: ToolSchema Record
The system MUST define a `ToolSchema` record that represents a tool definition in LLM-compatible format.

#### Scenario: Convert from Tool
- GIVEN a `Tool` instance with name, description, and parameters
- WHEN `ToolSchema.from(tool)` is called
- THEN it MUST return a `ToolSchema` with the tool's name, description, and parameters as a JSON Schema map

#### Scenario: No parameters tool
- GIVEN a `Tool` with an empty parameter list
- WHEN `ToolSchema.from(tool)` is called
- THEN it MUST return a `ToolSchema` with an empty parameters map

### Requirement: LlmStreamCallback Interface
The system MUST define a `LlmStreamCallback` interface for streaming LLM response handling.

#### Scenario: Token callback
- GIVEN a streaming LLM response producing tokens
- WHEN each token is received
- THEN `onToken(String)` MUST be called with the token text

#### Scenario: Stream completion
- GIVEN a streaming LLM response that has finished
- WHEN all tokens have been delivered
- THEN `onComplete(LlmResponse)` MUST be called with the complete response

#### Scenario: Stream error
- GIVEN a streaming LLM response that encounters an error
- WHEN the error occurs
- THEN `onError(Exception)` MUST be called with the error

### Requirement: LlmProviderRegistry Interface
The system MUST define a `LlmProviderRegistry` interface that manages named `LlmProvider` instances.

#### Scenario: Register a provider
- GIVEN a `LlmProviderRegistry` instance
- WHEN `register(String name, LlmProvider provider)` is called with a non-null name and provider
- THEN the provider MUST be stored under the given name and retrievable via `provider(name)`

#### Scenario: Reject duplicate registration
- GIVEN a `LlmProviderRegistry` with a provider registered under name "openai"
- WHEN `register("openai", anotherProvider)` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Lookup provider by name
- GIVEN a `LlmProviderRegistry` with provider "openai" registered
- WHEN `provider("openai")` is called
- THEN it MUST return the registered `LlmProvider` instance

#### Scenario: Lookup unknown provider
- GIVEN a `LlmProviderRegistry`
- WHEN `provider("unknown")` is called for a name that is not registered
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: List provider names
- GIVEN a `LlmProviderRegistry` with providers "openai" and "claude" registered
- WHEN `providerNames()` is called
- THEN it MUST return a set containing exactly "openai" and "claude"

#### Scenario: Remove a provider
- GIVEN a `LlmProviderRegistry` with provider "openai" registered
- WHEN `remove("openai")` is called
- THEN subsequent `provider("openai")` MUST throw `IllegalArgumentException`

#### Scenario: Close registry
- GIVEN a `LlmProviderRegistry` with multiple providers registered
- WHEN `close()` is called
- THEN `close()` MUST be called on every registered provider and all internal maps MUST be cleared

### Requirement: Default Provider Fallback
The system MUST support a designated default provider used when no specific provider name is specified.

#### Scenario: Set default provider
- GIVEN a `LlmProviderRegistry` with provider "openai" registered
- WHEN `setDefault("openai")` is called
- THEN `defaultProvider()` MUST return the "openai" provider

#### Scenario: Default not set, first registered used
- GIVEN a `LlmProviderRegistry` with "deepseek" as the first registered provider and no explicit default
- WHEN `defaultProvider()` is called
- THEN it MUST return the first registered provider

#### Scenario: Empty registry
- GIVEN a `LlmProviderRegistry` with no providers registered
- WHEN `defaultProvider()` is called
- THEN it MUST throw `IllegalStateException`

#### Scenario: Default removed
- GIVEN a `LlmProviderRegistry` with default set to "openai"
- WHEN `remove("openai")` is called
- THEN `defaultProvider()` MUST fall back to the first remaining provider, or throw `IllegalStateException` if empty

### Requirement: SkillRoute Record
The system MUST define an immutable `SkillRoute` record mapping a skill name to a provider and optional model override.

#### Scenario: Route with model override
- GIVEN a `SkillRoute("claude", "claude-opus-4-6-20250514")`
- THEN `providerName()` MUST return "claude" and `modelOverride()` MUST return "claude-opus-4-6-20250514"

#### Scenario: Route without model override
- GIVEN a `SkillRoute("deepseek", null)`
- THEN `providerName()` MUST return "deepseek" and `modelOverride()` MUST return null

### Requirement: Skill-to-Provider Routing
The system MUST support mapping skill names to specific providers via skill routing.

#### Scenario: Route a known skill
- GIVEN a `LlmProviderRegistry` with skill routing: "code-review" → SkillRoute("claude", "claude-opus-4-6-20250514")
- WHEN `route("code-review")` is called
- THEN it MUST return SkillRoute("claude", "claude-opus-4-6-20250514")

#### Scenario: Route an unknown skill
- GIVEN a `LlmProviderRegistry` with no routing for "translate"
- WHEN `route("translate")` is called
- THEN it MUST return null

#### Scenario: Register skill routing
- GIVEN a `LlmProviderRegistry` instance
- WHEN `addSkillRoute(String skillName, SkillRoute route)` is called
- THEN subsequent `route(skillName)` MUST return the registered route

### Requirement: LlmProviderFactory Interface
The system MUST define a `LlmProviderFactory` functional interface for creating `LlmProvider` instances from `LlmConfig`.

#### Scenario: Create provider from config
- GIVEN an `LlmProviderFactory` implementation and a valid `LlmConfig`
- WHEN `create(config)` is called
- THEN it MUST return a new `LlmProvider` instance

### Requirement: Registry Configuration Loading
The system MUST support loading the registry from a `Config` instance.

#### Scenario: Load from config
- GIVEN a `Config` with `llm.providers` section containing named provider configs, `llm.default`, and `llm.skill-routing`
- WHEN `DefaultLlmProviderRegistry.fromConfig(config, factories)` is called with matching factories
- THEN all providers MUST be registered, default MUST be set, and skill routing MUST be populated

#### Scenario: Missing default in config
- GIVEN a `Config` with providers but no `llm.default` key
- WHEN `fromConfig` is called
- THEN the first provider in config order MUST be used as default
