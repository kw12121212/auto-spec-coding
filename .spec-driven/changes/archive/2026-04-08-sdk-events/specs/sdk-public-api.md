# sdk-public-api.md delta (sdk-events)

## ADDED Requirements

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
