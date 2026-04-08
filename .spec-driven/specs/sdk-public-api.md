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

The system MUST provide a `SdkException` in `org.specdriven.sdk` as the unified base exception type for all SDK operations. It MUST support a `isRetryable()` method that returns `false` by default.

#### Scenario: Config error wraps as SdkConfigException
- GIVEN a builder with an invalid config path
- WHEN `.build()` is called
- THEN it MUST throw `SdkConfigException` with the original `ConfigException` as cause
- AND `isRetryable()` MUST return `false`

#### Scenario: Vault error wraps as SdkVaultException
- GIVEN a builder where vault resolution fails due to invalid master key
- WHEN `.build()` is called
- THEN it MUST throw `SdkVaultException` with the original `VaultException` as cause
- AND `isRetryable()` MUST return `false`

#### Scenario: LLM error wraps as SdkLlmException
- GIVEN an agent execution where the LLM provider call fails
- WHEN `run()` encounters the error
- THEN it MUST throw `SdkLlmException` with the original exception as cause
- AND `isRetryable()` MUST return `true` by default

#### Scenario: Non-LLM error wraps as SdkException
- GIVEN an agent execution where a non-LLM exception occurs (e.g. agent state error)
- WHEN `run()` encounters the error
- THEN it MUST throw `SdkException` with the original exception as cause
- AND `isRetryable()` MUST return `false`

#### Scenario: Tool errors are handled internally
- GIVEN an agent execution where a tool invocation throws an exception
- WHEN the orchestrator catches the tool error
- THEN the error MUST be fed back to the LLM as a ToolMessage
- AND `run()` MUST NOT throw for tool execution errors

#### Scenario: Permission error wraps as SdkPermissionException
- GIVEN an agent execution where a permission check denies an operation
- WHEN `run()` encounters the error
- THEN it MUST throw `SdkPermissionException` with the original exception as cause
- AND `isRetryable()` MUST return `false`

#### Scenario: Exception message and cause preserved
- GIVEN any `SdkException` subclass
- THEN `getMessage()` MUST return a descriptive message
- AND `getCause()` MUST return the original exception

#### Scenario: Retryable override
- GIVEN any `SdkException` subclass constructed with `retryable=false`
- WHEN `isRetryable()` is called
- THEN it MUST return the explicitly provided value

### Requirement: SdkException subclass hierarchy

The system MUST provide the following typed subclasses of `SdkException` in `org.specdriven.sdk`:

- `SdkConfigException` — for configuration loading/parsing errors (default retryable=false)
- `SdkVaultException` — for secret vault errors (default retryable=false)
- `SdkLlmException` — for LLM provider call errors (default retryable=true)
- `SdkToolException` — for tool execution errors (default retryable=false)
- `SdkPermissionException` — for permission denied errors (default retryable=false)

Each subclass MUST accept `(String message, Throwable cause)` and `(String message, Throwable cause, boolean retryable)` constructors.

#### Scenario: Catch by specific type
- GIVEN code that throws `SdkLlmException`
- WHEN caught by `catch (SdkLlmException e)`
- THEN the specific exception MUST be caught
- AND `catch (SdkException e)` MUST also catch it

#### Scenario: All subclasses are SdkException
- GIVEN an instance of any `SdkException` subclass
- THEN `instanceof SdkException` MUST return `true`

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

### Requirement: SdkEventListener functional interface

The system MUST provide a `@FunctionalInterface` `SdkEventListener` in `org.specdriven.sdk` that extends `Consumer<Event>`.

#### Scenario: Lambda as listener
- GIVEN an `SdkAgent` instance
- WHEN `agent.onEvent(e -> System.out.println(e.type()))` is called
- THEN the lambda MUST be accepted as an `SdkEventListener`

### Requirement: Global event listeners on SdkBuilder

The system MUST support registering event listeners on `SdkBuilder` that apply to all agents created by the `SpecDriven` instance.

#### Scenario: Global wildcard listener
- GIVEN a builder
- WHEN `.onEvent(listener)` is called then `.build()` is invoked
- THEN the listener MUST receive ALL events from every agent created by this SDK instance

#### Scenario: Global typed listener
- GIVEN a builder
- WHEN `.onEvent(EventType.TOOL_EXECUTED, listener)` is called then `.build()` is invoked
- THEN the listener MUST receive only `TOOL_EXECUTED` events from every agent

#### Scenario: Multiple global listeners
- GIVEN a builder with `.onEvent(listenerA)` and `.onEvent(EventType.ERROR, listenerB)`
- WHEN `.build()` is invoked
- THEN listenerA MUST receive all events and listenerB MUST receive only ERROR events

### Requirement: Per-agent event listeners on SdkAgent

The system MUST support registering event listeners on `SdkAgent` scoped to that agent's execution only.

#### Scenario: Per-agent wildcard listener
- GIVEN an `SdkAgent` created from a `SpecDriven` instance
- WHEN `agent.onEvent(listener)` is called then `agent.run("prompt")` is invoked
- THEN the listener MUST receive ALL events produced by this agent's run

#### Scenario: Per-agent typed listener
- GIVEN an `SdkAgent` instance
- WHEN `agent.onEvent(EventType.TOOL_EXECUTED, listener)` is called then `agent.run("prompt")` is invoked
- THEN the listener MUST receive only `TOOL_EXECUTED` events from this agent

#### Scenario: Per-agent and global listeners both fire
- GIVEN a `SpecDriven` with a global listener and an `SdkAgent` with a per-agent listener
- WHEN `agent.run("prompt")` is invoked
- THEN both listeners MUST receive the same events
- AND the global listener MUST also receive events from other agents

### Requirement: Agent state change events

`SdkAgent.run()` MUST emit `AGENT_STATE_CHANGED` events on each lifecycle state transition. The `init()` transition (null→IDLE) is not emitted since it precedes user-visible execution.

#### Scenario: Happy path emits state transitions
- GIVEN an `SdkAgent` with a listener for `AGENT_STATE_CHANGED`
- WHEN `run("prompt")` completes successfully
- THEN the listener MUST receive events for transitions: IDLE→RUNNING and RUNNING→STOPPED
- AND each event's `source` MUST be the agent's session ID
- AND each event's metadata MUST contain `fromState` and `toState` keys

### Requirement: Tool execution events

`SdkAgent.run()` MUST emit `TOOL_EXECUTED` events after each tool invocation during orchestration.

#### Scenario: Tool event metadata
- GIVEN an `SdkAgent` with a listener for `TOOL_EXECUTED`
- WHEN `run("prompt")` triggers tool execution
- THEN the listener MUST receive a `TOOL_EXECUTED` event
- AND metadata MUST contain `toolName` (String), `success` (Boolean), and `durationMs` (Long)

### Requirement: Error events

`SdkAgent.run()` MUST emit `ERROR` events when exceptions occur during execution.

#### Scenario: Tool failure emits error
- GIVEN an `SdkAgent` with a listener for `ERROR`
- WHEN a tool execution throws an exception
- THEN an `ERROR` event MUST be emitted with metadata containing `errorClass` and `errorMessage`

#### Scenario: Error event precedes state change
- GIVEN an `SdkAgent` with listeners for both `ERROR` and `AGENT_STATE_CHANGED`
- WHEN an error occurs during execution
- THEN the `ERROR` event MUST be published before the `AGENT_STATE_CHANGED` event transitioning to ERROR state
