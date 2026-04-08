# sdk-public-api.md

## ADDED Requirements

### Requirement: SpecDriven entry point

The system MUST provide a `SpecDriven` class in `org.specdriven.sdk` as the sole public entry point for the SDK.

#### Scenario: Create SDK with builder
- GIVEN no prior SDK instance
- WHEN `SpecDriven.builder()` is called followed by `.build()`
- THEN it MUST return a new `SpecDriven` instance with default configuration

#### Scenario: Create SDK with config file
- GIVEN a valid YAML config file at `config/agent.yaml`
- WHEN `SpecDriven.builder().config(Path.of("config/agent.yaml")).build()` is called
- THEN it MUST load the config and auto-assemble all components (LLM providers, vault, permissions)

### Requirement: SdkBuilder configuration

The system MUST provide a `SdkBuilder` in `org.specdriven.sdk` for configuring SDK instances.

#### Scenario: Config file auto-assembly
- GIVEN a builder and a valid YAML config path
- WHEN `.config(Path)` is called then `.build()` is invoked
- THEN LLM providers MUST be registered from the `llm.providers` section
- AND the default provider MUST be set from `llm.default`
- AND skill routing MUST be populated from `llm.skill-routing`

#### Scenario: Manual provider registry
- GIVEN a builder and a manually constructed `LlmProviderRegistry`
- WHEN `.providerRegistry(registry)` is called then `.build()` is invoked
- THEN the SDK MUST use the provided registry directly without auto-assembly

#### Scenario: Manual overrides config
- GIVEN a builder with both `.config(Path)` and `.providerRegistry(registry)` set
- WHEN `.build()` is invoked
- THEN the manually provided registry MUST take precedence over auto-assembled providers

#### Scenario: Register tools
- GIVEN a builder
- WHEN `.registerTool(tool)` is called multiple times then `.build()` is invoked
- THEN all registered tools MUST be available to agents created from this SDK instance

#### Scenario: Vault integration
- GIVEN a builder with `.config(Path)` where the YAML contains `vault:` references
- AND the environment variable `SPEC_DRIVEN_MASTER_KEY` is set
- WHEN `.build()` is invoked
- THEN vault references MUST be resolved using `VaultFactory` and `ConfigLoader.loadWithVault`

### Requirement: SdkAgent agent handle

The system MUST provide an `SdkAgent` in `org.specdriven.sdk` that wraps the internal agent lifecycle.

#### Scenario: Run a prompt
- GIVEN a `SpecDriven` instance with a configured LLM provider and at least one tool
- WHEN `sdk.createAgent().run("explain this code")` is called
- THEN it MUST return a non-null String response from the agent

#### Scenario: Run manages full lifecycle
- GIVEN an `SdkAgent` instance
- WHEN `run(String)` is called
- THEN the agent MUST be initialized, started, executed, and stopped automatically

#### Scenario: Agent with system prompt
- GIVEN a `SpecDriven` builder with `.systemPrompt("You are a code reviewer")`
- WHEN an agent is created and run
- THEN the system prompt MUST be included in the conversation before the user message

#### Scenario: Stop a running agent
- GIVEN an `SdkAgent` that is currently executing
- WHEN `stop()` is called from another thread
- THEN the agent MUST transition to STOPPED state

#### Scenario: Close SDK releases resources
- GIVEN a `SpecDriven` instance with registered providers
- WHEN `close()` is called
- THEN all providers MUST be closed and resources released

### Requirement: SdkException

The system MUST provide a `SdkException` in `org.specdriven.sdk` as the unified exception type for all SDK operations.

#### Scenario: Config error wraps as SdkException
- GIVEN a builder with an invalid config path
- WHEN `.build()` is called
- THEN it MUST throw `SdkException` with the original `ConfigException` as cause

#### Scenario: Agent error wraps as SdkException
- GIVEN an agent execution that fails
- WHEN `run()` encounters the error
- THEN it MUST throw `SdkException` with the original exception as cause

#### Scenario: Exception message
- GIVEN any `SdkException`
- THEN `getMessage()` MUST return a descriptive message
- AND `getCause()` MUST return the original exception

### Requirement: SdkConfig record

The system MUST provide an immutable `SdkConfig` record in `org.specdriven.sdk` for SDK-level configuration.

#### Scenario: Default config
- GIVEN `SdkConfig.defaults()`
- THEN `maxTurns()` MUST be 50
- AND `toolTimeoutSeconds()` MUST be 120
- AND `systemPrompt()` MUST be null

#### Scenario: Custom config
- GIVEN `new SdkConfig(10, 30, "You are helpful")`
- THEN `maxTurns()` MUST be 10
- AND `toolTimeoutSeconds()` MUST be 30
- AND `systemPrompt()` MUST be "You are helpful"

### Requirement: Tool registration via builder

The builder MUST support registering `Tool` instances that are available to all agents created from the SDK.

#### Scenario: Register multiple tools
- GIVEN a builder with `.registerTool(new BashTool())` and `.registerTool(new ReadTool())`
- WHEN an agent is created and run
- THEN both tools MUST be available for the orchestrator to execute

#### Scenario: Tools from config
- GIVEN a builder with `.config(Path)` where config specifies enabled tools
- WHEN `.build()` is invoked
- THEN the specified built-in tools MUST be auto-registered
