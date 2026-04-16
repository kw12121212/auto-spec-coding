---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
    - src/main/java/org/specdriven/sdk/SdkAgent.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SdkConfig.java
    - src/main/java/org/specdriven/sdk/SdkConfigException.java
    - src/main/java/org/specdriven/sdk/SdkEventListener.java
    - src/main/java/org/specdriven/sdk/SdkException.java
    - src/main/java/org/specdriven/sdk/SdkLlmException.java
    - src/main/java/org/specdriven/sdk/SdkPermissionException.java
    - src/main/java/org/specdriven/sdk/SdkToolException.java
    - src/main/java/org/specdriven/sdk/SdkVaultException.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentEventTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentQuestionTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderEventTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SdkConfigTest.java
    - src/test/java/org/specdriven/sdk/SdkEventListenerTest.java
    - src/test/java/org/specdriven/sdk/SdkExceptionTest.java
    - src/test/java/org/specdriven/sdk/SdkSubclassExceptionTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenTest.java
    - src/test/java/org/specdriven/sdk/SpecDrivenPlatformTest.java
---

# sdk-public-api.md

### Requirement: SpecDriven entry point

The system MUST provide `SpecDriven` as the primary public agent facade in `org.specdriven.sdk`.

#### Scenario: Create SDK with builder
- GIVEN no prior SDK instance
- WHEN `SpecDriven.builder()` is called followed by `.build()`
- THEN it MUST return a new `SpecDriven` instance with default configuration

#### Scenario: Create SDK with config file
- GIVEN a valid YAML config file at `config/agent.yaml`
- WHEN `SpecDriven.builder().config(Path.of("config/agent.yaml")).build()` is called
- THEN it MUST load the config and auto-assemble all components (LLM providers, vault, permissions)

### Requirement: Public LealonePlatform entry point

The SDK public surface MUST additionally expose `LealonePlatform` as a public platform-level entry point for callers that need direct access to assembled Lealone-centered capabilities beyond the agent facade.

#### Scenario: Public platform entry coexists with SpecDriven
- GIVEN application code that needs direct platform capability access
- WHEN it uses the supported public `LealonePlatform` entry path
- THEN it MUST obtain a platform instance without removing or renaming the existing `SpecDriven` entry path

### Requirement: Platform and agent facade compatibility

The introduction of `LealonePlatform` MUST NOT break existing `SpecDriven`-based SDK usage.

#### Scenario: Existing SpecDriven usage remains compatible
- GIVEN existing application code that uses `SpecDriven.builder()` and `createAgent()`
- WHEN the SDK adds `LealonePlatform`
- THEN the existing `SpecDriven` usage path MUST remain supported
- AND its observable behavior MUST remain unchanged unless explicitly modified by a separate change

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

### Requirement: SdkBuilder delivery mode override

The `SdkBuilder` MUST support a global delivery mode override that applies to all agents.

#### Scenario: Override delivery mode
- GIVEN a builder with `.deliveryModeOverride(DeliveryMode.PAUSE_WAIT_HUMAN)`
- WHEN an agent is created and encounters a question
- THEN the question MUST be routed using `PAUSE_WAIT_HUMAN` regardless of the default routing policy

#### Scenario: No override uses routing policy
- GIVEN a builder without `deliveryModeOverride`
- WHEN an agent is created and encounters a question
- THEN the question MUST be routed using the default `QuestionRoutingPolicy`

### Requirement: SdkAgent pending question query

The `SdkAgent` MUST expose pending questions for a session.

#### Scenario: Query returns waiting questions
- GIVEN a session with one question in `WAITING_FOR_ANSWER`
- WHEN `pendingQuestions(sessionId)` is called
- THEN it MUST return a list containing that question

#### Scenario: Empty list when no pending questions
- GIVEN a session with no waiting questions
- WHEN `pendingQuestions(sessionId)` is called
- THEN it MUST return an empty list

### Requirement: SdkAgent human reply submission

The `SdkAgent` MUST support submitting a human reply to a waiting question.

#### Scenario: Submit valid reply
- GIVEN a session with a question in `WAITING_FOR_ANSWER`
- AND a valid `Answer` with matching `questionId`, `sessionId`, and `deliveryMode`
- WHEN `submitHumanReply(sessionId, questionId, answer)` is called
- THEN the answer MUST be accepted
- AND the question status MUST transition to `ANSWERED`
- AND a `QUESTION_ANSWERED` event MUST be emitted

#### Scenario: Reject reply for unknown session
- GIVEN no waiting question for the given session
- WHEN `submitHumanReply(sessionId, questionId, answer)` is called
- THEN it MUST throw `SdkException` with `isRetryable()` returning `false`

#### Scenario: Reject reply for expired question
- GIVEN a question whose status is `EXPIRED`
- WHEN `submitHumanReply(sessionId, questionId, answer)` is called
- THEN it MUST throw `SdkException` with `isRetryable()` returning `false`

### Requirement: SdkBuilder channel provider registration

The `SdkBuilder` MUST support registering mobile channel providers for use by all agents.

#### Scenario: Register channel provider
- GIVEN a builder
- WHEN `.registerChannelProvider("telegram", provider)` is called then `.build()` is invoked
- THEN the provider MUST be available in the internal registry

#### Scenario: Channel providers from config
- GIVEN a builder with `.config(Path)` where the YAML contains a `mobile-channels` section
- WHEN `.build()` is invoked
- THEN providers matching the configured channel types MUST be resolved from the registry

### Requirement: SpecDriven platform accessor

`SpecDriven` MUST expose a `platform()` method returning the assembled `LealonePlatform`.

#### Scenario: platform() returns non-null platform after build()
- GIVEN a `SpecDriven` instance built via `SpecDriven.builder().build()`
- WHEN `platform()` is called
- THEN it MUST return a non-null `LealonePlatform` instance

#### Scenario: platform() exposes checkHealth after build
- GIVEN a `SpecDriven` instance built via `SpecDriven.builder().build()`
- WHEN `sdk.platform().checkHealth()` is called
- THEN it MUST return a non-null `PlatformHealth` result without throwing

### Requirement: SdkBuilder channel configs

The `SdkBuilder` MUST support setting mobile channel configurations.

#### Scenario: Set channel configs
- GIVEN a builder
- WHEN `.channelConfigs(configs)` is called with a list of `MobileChannelConfig` then `.build()` is invoked
- THEN the configs MUST be used to assemble channel handles at build time

### Requirement: SdkBuilder wires mobile channels into delivery service

When mobile channel configs and providers are registered, the `SdkBuilder` MUST wire the assembled channels into the `QuestionDeliveryService`.

#### Scenario: Mobile channel replaces default channel
- GIVEN a builder with a registered provider and a matching channel config
- WHEN `.build()` is invoked
- THEN the `QuestionDeliveryService` MUST use the assembled mobile channel instead of `LoggingDeliveryChannel`
- AND the `QuestionDeliveryService` MUST use the assembled reply collector instead of `InMemoryReplyCollector`

#### Scenario: No channels preserves defaults
- GIVEN a builder without channel configs
- WHEN `.build()` is invoked
- THEN the `QuestionDeliveryService` MUST use `LoggingDeliveryChannel` and `InMemoryReplyCollector` as before
