---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmConfigSnapshot.java
    - src/main/java/org/specdriven/agent/agent/LlmConfig.java
    - src/main/java/org/specdriven/agent/agent/LlmProvider.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/event/Event.java
    - src/main/java/org/specdriven/agent/event/EventBus.java
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/agent/SetLlmSqlException.java
    - src/main/java/org/specdriven/agent/agent/SetLlmStatementParser.java
    - src/main/java/org/specdriven/agent/llm/LealoneRuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/agent/llm/RuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/agent/llm/RuntimeLlmConfigVersion.java
    - src/main/java/org/specdriven/agent/agent/OpenAiClient.java
    - src/main/java/org/specdriven/agent/agent/ClaudeClient.java
    - src/main/java/org/specdriven/agent/agent/OpenAiProvider.java
    - src/main/java/org/specdriven/agent/agent/ClaudeProvider.java
    - src/main/java/org/specdriven/sdk/SdkBuilder.java
    - src/main/java/org/specdriven/sdk/SdkAgent.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutor.java
    - src/main/java/org/specdriven/agent/vault/SecretVault.java
    - src/main/java/org/specdriven/agent/vault/VaultResolver.java
  tests:
    - src/test/java/org/specdriven/agent/agent/LlmConfigTest.java
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/agent/SetLlmStatementParserTest.java
    - src/test/java/org/specdriven/agent/llm/LealoneRuntimeLlmConfigStoreTest.java
    - src/test/java/org/specdriven/agent/agent/OpenAiProviderTest.java
    - src/test/java/org/specdriven/agent/agent/ClaudeProviderTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderTest.java
    - src/test/java/org/specdriven/sdk/SdkBuilderEventTest.java
    - src/test/java/org/specdriven/agent/vault/VaultResolverTest.java
---

# Runtime LLM Config

## ADDED Requirements

### Requirement: Immutable runtime LLM config snapshot
The system MUST expose runtime LLM configuration as an immutable snapshot value containing the effective non-sensitive fields used for future LLM requests.

#### Scenario: Snapshot captures effective request settings
- GIVEN a runtime LLM config snapshot created for provider `openai`
- WHEN the snapshot is inspected
- THEN it MUST expose the effective non-sensitive request settings for that scope, including provider selection and request parameters such as model, base URL, timeout, temperature, or equivalent supported fields
- AND changing a later snapshot MUST NOT mutate the earlier snapshot instance

#### Scenario: Snapshot excludes secret governance behavior
- GIVEN runtime LLM config snapshots are enabled
- WHEN the system exposes snapshot behavior
- THEN the snapshot contract MUST cover only non-sensitive fields
- AND runtime snapshot exposure MUST remain separate from secret-bearing provider authentication material
- AND secret redaction and permission governance MUST remain outside the snapshot contract

### Requirement: Atomic replacement for future requests
The system MUST support atomically replacing the active runtime LLM config snapshot for a scope so that future requests observe the replacement as a single switch.

#### Scenario: Future requests observe replacement
- GIVEN scope `session-a` currently resolves to snapshot `S1`
- AND a later replacement installs snapshot `S2`
- WHEN a new LLM request starts after the replacement completes
- THEN that request MUST resolve `S2`
- AND it MUST NOT observe a mixture of fields from `S1` and `S2`

#### Scenario: Existing session client picks up a later session snapshot
- GIVEN a registry-managed client for `session-a` has already been created
- AND a first request from that client starts while `session-a` resolves snapshot `S1`
- AND a later successful runtime replacement makes `S2` active for later requests in `session-a`
- WHEN a second request starts from the same registry-managed client after the replacement completes
- THEN the first request MUST keep using `S1`
- AND the second request MUST resolve `S2`

#### Scenario: Concurrent readers never observe partial update
- GIVEN multiple threads resolve the active snapshot while another thread replaces it
- WHEN reads occur during the replacement window
- THEN each read MUST return either the full pre-replacement snapshot or the full post-replacement snapshot
- AND no read may return a partially updated configuration

### Requirement: In-flight request snapshot binding
Each LLM request MUST bind to exactly one resolved runtime snapshot for the full lifetime of that request.

#### Scenario: In-flight request keeps original snapshot
- GIVEN an LLM request starts using snapshot `S1`
- AND the active snapshot is replaced with `S2` before the request completes
- WHEN the in-flight request continues
- THEN it MUST continue using `S1` until completion
- AND only later requests MAY observe `S2`

#### Scenario: Later requests may switch providers without recreating the client
- GIVEN a registry-managed client for `session-a` starts a request while `session-a` resolves snapshot `S1` with provider `openai`
- AND a later successful runtime replacement makes snapshot `S2` active for `session-a` with provider `claude`
- WHEN a later request starts from the same registry-managed client after the replacement completes
- THEN the later request MUST use provider `claude`
- AND the earlier in-flight request MUST continue using provider `openai` until completion

### Requirement: Session-scoped runtime isolation baseline
The system MUST support resolving runtime LLM config snapshots by scope so one session can observe a different active snapshot than another session.

#### Scenario: Session override does not affect other sessions
- GIVEN `session-a` resolves to snapshot `S1`
- AND `session-b` resolves to snapshot `S2`
- WHEN both sessions start new LLM requests
- THEN requests in `session-a` MUST use `S1`
- AND requests in `session-b` MUST use `S2`

#### Scenario: Missing scoped snapshot falls back to default
- GIVEN no session-specific snapshot exists for `session-c`
- AND a default runtime snapshot `S-default` is configured
- WHEN `session-c` starts a new LLM request
- THEN the request MUST use `S-default`

### Requirement: Runtime config changes do not alter provider protocol semantics
Introducing runtime snapshots MUST NOT change existing provider request/response protocol behavior beyond selecting the effective config values used for new requests.

#### Scenario: Existing provider behavior remains stable
- GIVEN a provider already supports request serialization, retries, streaming, and tool calls
- WHEN runtime snapshot support is enabled
- THEN those behaviors MUST remain the same for any given effective snapshot
- AND the only observable difference MUST be which snapshot a new request resolves before it starts

### Requirement: Default runtime snapshot persistence
The system MUST support persisting the current default runtime LLM config snapshot as a non-sensitive value that survives process restart.

#### Scenario: Persist default runtime snapshot
- GIVEN a default runtime snapshot `S1` is active
- WHEN the system persists the default runtime snapshot
- THEN the persisted record MUST contain the same effective non-sensitive fields as `S1`
- AND no secret field or resolved secret value may be required by this persistence contract

#### Scenario: Session override is not persisted by this change
- GIVEN a session-specific runtime snapshot override exists for `session-a`
- WHEN default runtime snapshot persistence is used
- THEN the persistence contract MUST apply only to the default runtime snapshot
- AND the session-specific override MUST remain outside this change

### Requirement: Restart recovery of last valid persisted default snapshot
The system MUST restore the last valid persisted default runtime snapshot when the runtime config persistence component is initialized again for the same backing store.

#### Scenario: Restart restores latest persisted default snapshot
- GIVEN default runtime snapshot `S1` has been persisted successfully
- WHEN a new runtime config persistence component is created against the same backing store
- THEN it MUST resolve `S1` as the recovered default runtime snapshot

#### Scenario: No persisted default snapshot falls back cleanly
- GIVEN no default runtime snapshot has been persisted yet
- WHEN the runtime config persistence component initializes
- THEN recovery MUST return no persisted snapshot
- AND the runtime registry MAY continue using its non-persisted default behavior

### Requirement: Versioned history for persisted default snapshots
Each successful persistence of the default runtime snapshot MUST create an ordered version history entry so earlier persisted values remain queryable for later restore behavior.

#### Scenario: New persistence creates new version entry
- GIVEN default runtime snapshot `S1` has already been persisted
- AND a later default runtime snapshot `S2` is persisted successfully
- WHEN the persisted history is queried in descending version order
- THEN `S2` MUST appear before `S1`
- AND both versions MUST remain available in the history

#### Scenario: Failed persistence does not publish partial history entry
- GIVEN default runtime snapshot `S1` is already the latest persisted version
- WHEN a later persistence attempt for `S2` fails before completion
- THEN the latest persisted version MUST remain `S1`
- AND no partial or unreadable history entry for `S2` may become observable

### Requirement: Internal restore from persisted version history
The system MUST support internally restoring the active persisted default runtime snapshot from an earlier persisted version without deleting the recorded history.

#### Scenario: Restore earlier version makes it active again
- GIVEN persisted default runtime snapshot versions `S1`, `S2`, and `S3` exist in order from oldest to newest
- WHEN the system restores `S1` as the active persisted default snapshot
- THEN later default runtime snapshot resolution MUST return the restored `S1` values
- AND the version history MUST remain queryable after the restore

#### Scenario: Restore does not require new external command surface
- GIVEN internal restore behavior is supported
- WHEN this change defines restore semantics
- THEN it MUST NOT require a new SQL, HTTP, or SDK command surface in this change

### Requirement: Persistence does not change existing request binding semantics
Persisting and recovering the default runtime snapshot MUST NOT change the existing in-flight request binding behavior defined for runtime snapshots.

#### Scenario: Recovery affects only later requests
- GIVEN a request started before a recovered or restored default snapshot becomes active
- WHEN that request continues to completion
- THEN it MUST keep using the snapshot it originally resolved
- AND only later requests MAY observe the recovered or restored default snapshot

### Requirement: SET LLM updates supported non-sensitive runtime parameters
The system MUST support updating the active runtime LLM configuration for a session through a `SET LLM` SQL statement that carries supported non-sensitive parameter assignments.

#### Scenario: SET LLM updates later requests in the same session
- GIVEN a session currently resolves runtime snapshot `S1`
- AND a `SET LLM` statement assigns supported non-sensitive parameters such as provider, model, base URL, timeout, or retry-related fields
- WHEN the statement completes successfully for that session
- THEN later LLM requests started by that session MUST resolve a replacement snapshot reflecting those assigned values
- AND requests started by other sessions MUST continue resolving their own snapshots unchanged

#### Scenario: Missing parameter in SET LLM keeps prior effective value
- GIVEN a session currently resolves runtime snapshot `S1`
- WHEN a successful `SET LLM` statement assigns only a subset of supported parameters
- THEN the replacement snapshot for that session MUST preserve the prior effective value for every supported parameter not mentioned in the statement

### Requirement: SET LLM applies updates atomically
The system MUST apply each successful `SET LLM` statement as one atomic runtime snapshot replacement for the targeted scope.

#### Scenario: Successful statement installs one coherent replacement snapshot
- GIVEN a `SET LLM` statement assigns multiple supported parameters
- WHEN the statement succeeds
- THEN later requests MUST observe either the full pre-statement snapshot or the full post-statement snapshot
- AND no later request may observe a mixture of old and new parameter values from that statement

#### Scenario: Failed statement leaves prior snapshot active
- GIVEN a session currently resolves runtime snapshot `S1`
- WHEN a `SET LLM` statement fails validation or execution before completion
- THEN later requests in that session MUST continue resolving `S1`
- AND no partial update from the failed statement may become active

### Requirement: SET LLM rejects unsupported or invalid assignments
The system MUST reject `SET LLM` assignments that target unsupported keys or provide invalid values for supported non-sensitive runtime parameters.

#### Scenario: Unsupported key is rejected
- GIVEN a `SET LLM` statement includes a key that is outside the supported non-sensitive runtime LLM config contract
- WHEN the statement is evaluated
- THEN the statement MUST fail
- AND the active runtime snapshot for that scope MUST remain unchanged

#### Scenario: Invalid value is rejected
- GIVEN a `SET LLM` statement includes a supported key with an invalid value such as a blank provider name or a non-positive timeout
- WHEN the statement is evaluated
- THEN the statement MUST fail
- AND the active runtime snapshot for that scope MUST remain unchanged

### Requirement: SET LLM preserves in-flight request binding
Applying runtime LLM config changes through `SET LLM` MUST NOT change the snapshot already bound to an in-flight request.

#### Scenario: In-flight request continues with pre-update snapshot
- GIVEN an LLM request starts in a session using snapshot `S1`
- AND the same session later executes a successful `SET LLM` statement that installs snapshot `S2`
- WHEN the in-flight request continues to completion
- THEN it MUST continue using `S1`
- AND only later requests started after the statement completes MAY observe `S2`

### Requirement: SET LLM does not introduce secret governance behavior
`SET LLM` behavior MUST remain limited to non-sensitive runtime config fields.

#### Scenario: Secret-bearing governance remains outside SET LLM
- GIVEN runtime LLM updates through `SET LLM` are enabled
- WHEN the SQL update contract is evaluated
- THEN secret values and secret reference assignments MUST remain outside the `SET LLM` contract
- AND secret redaction, permission checks, and audit governance MUST remain specified outside this contract

### Requirement: LLM provider config resolves Vault-backed authentication references
The system MUST support constructing LLM providers from provider config values whose sensitive authentication fields use `vault:<key>` references.

#### Scenario: Provider API key resolves from Vault before provider creation
- GIVEN LLM provider config for provider `openai` with `apiKey` set to `vault:openai_key`
- AND a `SecretVault` where `openai_key` resolves to `sk-real-key`
- WHEN the provider registry is constructed through the Vault-aware LLM config path
- THEN the registered provider's effective authentication config MUST use `sk-real-key`
- AND non-sensitive provider config fields such as provider type, base URL, model, timeout, and retry values MUST remain otherwise unchanged

#### Scenario: Plain provider API key remains supported
- GIVEN LLM provider config for provider `openai` with `apiKey` set to `sk-local-test`
- WHEN the provider registry is constructed through the Vault-aware LLM config path
- THEN the registered provider's effective authentication config MUST use `sk-local-test`
- AND the provider MUST be registered successfully

#### Scenario: Missing Vault key fails provider registry construction
- GIVEN LLM provider config for provider `openai` with `apiKey` set to `vault:missing_key`
- AND a `SecretVault` with no `missing_key` entry
- WHEN the provider registry is constructed through the Vault-aware LLM config path
- THEN construction MUST fail with a Vault-specific error
- AND the provider MUST NOT become usable with the unresolved literal `vault:missing_key`

### Requirement: Runtime snapshots remain non-sensitive after Vault resolution
Resolving Vault-backed provider authentication material MUST NOT add secret values or Vault reference names to runtime LLM snapshots.

#### Scenario: Snapshot excludes resolved secret
- GIVEN a provider was constructed from `apiKey` set to `vault:openai_key`
- AND the Vault reference resolved to `sk-real-key`
- WHEN the system exposes the provider's runtime `LlmConfigSnapshot`
- THEN the snapshot MUST expose only non-sensitive runtime fields such as provider name, base URL, model, timeout, and retry values
- AND the snapshot MUST NOT expose `sk-real-key`
- AND the snapshot MUST NOT expose `vault:openai_key`

#### Scenario: Persisted runtime config history excludes resolved secret
- GIVEN a provider was constructed from Vault-backed authentication material
- WHEN the system persists a default runtime LLM config snapshot
- THEN the persisted runtime config record MUST contain only non-sensitive snapshot fields
- AND it MUST NOT contain the resolved secret value
- AND it MUST NOT contain the Vault reference name

### Requirement: Vault resolution does not expand SET LLM mutation surface
Vault-backed LLM provider authentication integration MUST NOT make `SET LLM` a secret mutation or secret reference update surface.

#### Scenario: SET LLM remains limited to non-sensitive runtime parameters
- GIVEN Vault-backed provider authentication resolution is enabled
- WHEN a `SET LLM` statement is evaluated
- THEN the supported assignment keys MUST remain limited to non-sensitive runtime parameters
- AND authentication secrets or Vault reference assignments MUST remain outside the `SET LLM` contract

### Requirement: Successful runtime config changes publish `LLM_CONFIG_CHANGED`
The system MUST publish one `EventType.LLM_CONFIG_CHANGED` event whenever a successful runtime LLM config change becomes active for future requests.

#### Scenario: Successful default snapshot replacement publishes one event
- GIVEN a default runtime snapshot `S1` is currently active
- WHEN a successful default snapshot replacement makes `S2` active for later requests
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST describe the post-change default scope rather than the previous snapshot

#### Scenario: Successful session snapshot replacement publishes one session-scoped event
- GIVEN session `session-a` currently resolves runtime snapshot `S1`
- WHEN a successful session snapshot replacement makes `S2` active for later requests in `session-a`
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST identify the affected session scope

#### Scenario: Successful `SET LLM` publishes one session-scoped event
- GIVEN session `session-a` currently resolves runtime snapshot `S1`
- WHEN a successful `SET LLM` statement installs replacement snapshot `S2` for later requests in `session-a`
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST describe the committed post-statement session snapshot

#### Scenario: Clearing a session override publishes one fallback event
- GIVEN session `session-a` currently resolves a session-specific runtime snapshot override
- WHEN that session override is cleared successfully and the session falls back to the default runtime snapshot
- THEN exactly one `LLM_CONFIG_CHANGED` event MUST be published
- AND the event MUST describe the post-clear effective session snapshot

#### Scenario: Failed runtime update publishes no success event
- GIVEN a runtime config update attempt fails before a replacement snapshot becomes active
- WHEN the failure is returned to the caller
- THEN no `LLM_CONFIG_CHANGED` event may be published for that failed attempt

### Requirement: `LLM_CONFIG_CHANGED` metadata identifies scope and affected fields
Each published `LLM_CONFIG_CHANGED` event MUST carry enough non-sensitive metadata for downstream consumers to identify which scope changed and which runtime fields changed.

#### Scenario: Default-scope event metadata
- GIVEN a successful default runtime snapshot replacement publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata is inspected
- THEN it MUST contain `scope = "default"`
- AND it MUST contain the effective post-change `provider`
- AND it MUST contain `changedKeys` as a string value naming the non-sensitive runtime fields whose effective values changed
- AND it MUST NOT contain `sessionId`

#### Scenario: Session-scope event metadata
- GIVEN a successful session-scoped runtime config change publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata is inspected
- THEN it MUST contain `scope = "session"`
- AND it MUST contain the affected `sessionId`
- AND it MUST contain the effective post-change `provider`
- AND it MUST contain `changedKeys` as a string value naming the non-sensitive runtime fields whose effective values changed

#### Scenario: Event metadata excludes full snapshot payloads
- GIVEN a published `LLM_CONFIG_CHANGED` event
- WHEN the event metadata is inspected
- THEN it MUST identify the affected scope and changed fields without embedding the full pre-change or post-change snapshot as metadata
- AND it MUST remain limited to non-sensitive runtime config information

### Requirement: Config-change events preserve existing request binding semantics
Publishing `LLM_CONFIG_CHANGED` MUST NOT alter the existing runtime config semantics for atomic replacement, session isolation, or in-flight request snapshot binding.

#### Scenario: Event publication does not change in-flight request binding
- GIVEN an in-flight LLM request is already bound to runtime snapshot `S1`
- AND a later successful runtime config change publishes `LLM_CONFIG_CHANGED` for replacement snapshot `S2`
- WHEN the in-flight request continues to completion
- THEN it MUST continue using `S1`
- AND only later requests MAY observe `S2`

### Requirement: SET LLM permission check before mutation

The system MUST check `llm.config.set` permission before applying a `SET LLM` statement mutation. When a `PermissionProvider` is configured, the registry MUST construct a `Permission` with action `llm.config.set` and resource `session:<sessionId>` and delegate to `permissionProvider.check()` before any snapshot replacement occurs.

#### Scenario: Allowed SET LLM proceeds normally
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `ALLOW` for `llm.config.set`
- WHEN `applySetLlmStatement(sessionId, sql)` is called
- THEN the statement MUST be applied and the replacement snapshot MUST become active for later requests

#### Scenario: Denied SET LLM is rejected without state change
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- AND session `session-a` currently resolves runtime snapshot `S1`
- WHEN `applySetLlmStatement("session-a", sql)` is called
- THEN a `SetLlmSqlException` MUST be thrown describing the permission denial
- AND the active runtime snapshot for `session-a` MUST remain `S1`

#### Scenario: Confirm-required SET LLM is rejected
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `CONFIRM` for `llm.config.set`
- WHEN `applySetLlmStatement(sessionId, sql)` is called
- THEN a `SetLlmSqlException` MUST be thrown describing that explicit confirmation is required
- AND no state change MUST occur

#### Scenario: Missing PermissionProvider allows mutation for backward compatibility
- GIVEN a `DefaultLlmProviderRegistry` constructed without a `PermissionProvider`
- WHEN `applySetLlmStatement(sessionId, sql)` is called
- THEN the statement MUST be applied without any permission check
- AND existing behavior MUST be preserved

### Requirement: Clear session snapshot permission check

The system MUST check `llm.config.set` permission before clearing a session snapshot override. When a `PermissionProvider` is configured, the registry MUST construct a `Permission` with action `llm.config.set`, resource `session:<sessionId>`, and `operation = "clear"` before removing the session override.

#### Scenario: Allowed clear proceeds normally
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `ALLOW` for `llm.config.set`
- AND session `session-a` has a session-specific snapshot override
- WHEN `clearSessionSnapshot("session-a")` is called
- THEN the override MUST be cleared and a `LLM_CONFIG_CHANGED` event MUST be published

#### Scenario: Denied clear is rejected without state change
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- AND session `session-a` has a session-specific snapshot override
- WHEN `clearSessionSnapshot("session-a")` is called
- THEN a `SetLlmSqlException` MUST be thrown
- AND the session override MUST remain in place

### Requirement: PermissionProvider constructor integration

`DefaultLlmProviderRegistry` MUST accept an optional `PermissionProvider` through its constructor chain. The existing constructors MUST remain backward-compatible.

#### Scenario: Constructor with PermissionProvider
- GIVEN a `PermissionProvider` instance
- WHEN `new DefaultLlmProviderRegistry(runtimeConfigStore, eventBus, permissionProvider)` is called
- THEN the registry MUST use the provided `PermissionProvider` for all mutation permission checks

#### Scenario: Existing constructor without PermissionProvider
- GIVEN no `PermissionProvider` is provided
- WHEN `new DefaultLlmProviderRegistry(runtimeConfigStore, eventBus)` is called
- THEN the registry MUST function identically to its pre-change behavior
- AND no permission checks MUST be performed on mutation operations

### Requirement: LlmConfig toString redacts API key
`LlmConfig.toString()` MUST return a string representation where the `apiKey` field is replaced with a fixed redaction placeholder, preventing the actual secret value from appearing in logs, debug output, or exception messages.

#### Scenario: toString hides API key
- GIVEN a `LlmConfig` with `apiKey` set to `sk-super-secret-key`
- WHEN `toString()` is called on that config
- THEN the returned string MUST contain a fixed redaction placeholder for the apiKey field
- AND the returned string MUST NOT contain `sk-super-secret-key`

#### Scenario: toString preserves non-sensitive fields
- GIVEN a `LlmConfig` with `baseUrl` set to `https://api.openai.com/v1`, `model` set to `gpt-4`, `timeout` set to `60`, `maxRetries` set to `3`
- WHEN `toString()` is called
- THEN the returned string MUST contain `https://api.openai.com/v1`
- AND it MUST contain `gpt-4`
- AND it MUST contain `60`
- AND it MUST contain `3`

### Requirement: LlmConfig constructor rejects without leaking API key
The `LlmConfig` compact constructor MUST throw `IllegalArgumentException` for invalid inputs without including the `apiKey` value in the exception message.

#### Scenario: Blank API key exception excludes secret
- GIVEN a construction attempt with a blank `apiKey`
- WHEN the `IllegalArgumentException` is thrown
- THEN the exception message MUST NOT contain the provided blank value
- AND the message MUST describe the constraint violation without echoing the input value

### Requirement: SET LLM exception messages exclude secrets
`SetLlmSqlException` and any exception thrown by `DefaultLlmProviderRegistry` during config mutation MUST NOT include resolved secret values or Vault reference names in the exception message.

#### Scenario: Permission denied exception excludes config secrets
- GIVEN a `SET LLM` statement is rejected due to permission denial
- WHEN the exception message is inspected
- THEN it MUST NOT contain any API key value
- AND it MUST describe the denial reason using only non-sensitive identifiers such as session ID and permission action

### Requirement: LLM_CONFIG_CHANGED event metadata secret guard
`DefaultLlmProviderRegistry` MUST ensure that `LLM_CONFIG_CHANGED` event metadata never contains secret values. When publishing an event, the registry MUST validate that metadata values do not match the resolved API key for the affected provider.

#### Scenario: Event metadata does not contain API key after config change
- GIVEN a provider with API key `sk-real-key` is active
- AND a successful runtime config change publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata values are inspected
- THEN no metadata value MUST equal `sk-real-key`

#### Scenario: Event metadata does not contain vault reference after config change
- GIVEN a provider was constructed from `vault:openai_key` reference
- AND a successful runtime config change publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata values are inspected
- THEN no metadata value MUST equal `vault:openai_key`

### Requirement: Failed config change audit event
The system MUST publish an `LLM_CONFIG_CHANGE_REJECTED` event whenever a runtime LLM config change attempt fails due to permission denial, confirmation requirement, validation error, or parsing failure before a replacement snapshot becomes active.

#### Scenario: Permission denied publishes rejected event
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- WHEN `applySetLlmStatement("session-a", "SET LLM model = 'gpt-4'")` is called
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published before the `SetLlmSqlException` is thrown
- AND the event metadata MUST contain `scope = "session"`, `sessionId = "session-a"`, `result = "denied"`, and a non-empty `reason`

#### Scenario: Confirm required publishes rejected event
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `CONFIRM` for `llm.config.set`
- WHEN `applySetLlmStatement("session-a", "SET LLM model = 'gpt-4'")` is called
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published
- AND the event metadata MUST contain `result = "confirm_required"`

#### Scenario: Validation failure publishes rejected event
- GIVEN a `SET LLM` statement with an unsupported key or invalid value
- WHEN the statement is evaluated and fails validation
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published
- AND the event metadata MUST contain `result = "validation_failed"` and a non-empty `reason`

#### Scenario: Parse error publishes rejected event
- GIVEN a malformed `SET LLM` SQL statement
- WHEN the statement cannot be parsed
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published
- AND the event metadata MUST contain `result = "parse_error"` and a non-empty `reason`

#### Scenario: Clear session permission denied publishes rejected event
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- AND session `session-a` has a session-specific snapshot override
- WHEN `clearSessionSnapshot("session-a")` is called
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published before the exception is thrown
- AND the event metadata MUST contain `result = "denied"`

#### Scenario: Successful change does not publish rejected event
- GIVEN a successful `SET LLM` statement
- WHEN the replacement snapshot becomes active
- THEN no `LLM_CONFIG_CHANGE_REJECTED` event MUST be published

### Requirement: Config change event operator metadata
Both `LLM_CONFIG_CHANGED` and `LLM_CONFIG_CHANGE_REJECTED` events MUST include an `operator` field in their metadata identifying the initiator of the change attempt.

#### Scenario: Session-scoped success event includes session operator
- GIVEN a successful `SET LLM` statement in session `session-a`
- WHEN the `LLM_CONFIG_CHANGED` event is published
- THEN the event metadata MUST contain `operator = "session:session-a"`

#### Scenario: Default-scope success event includes system operator
- GIVEN a successful default snapshot replacement
- WHEN the `LLM_CONFIG_CHANGED` event is published
- THEN the event metadata MUST contain `operator = "system"`

#### Scenario: Session-scoped rejected event includes session operator
- GIVEN a `SET LLM` statement in session `session-a` that is rejected
- WHEN the `LLM_CONFIG_CHANGE_REJECTED` event is published
- THEN the event metadata MUST contain `operator = "session:session-a"`

### Requirement: Rejected event metadata excludes secrets
`LLM_CONFIG_CHANGE_REJECTED` event metadata MUST NOT contain API key values, resolved secret values, or Vault reference names.

#### Scenario: Rejected event metadata does not contain API key
- GIVEN a provider with API key `sk-real-key` is active
- AND a `SET LLM` statement is rejected
- WHEN the `LLM_CONFIG_CHANGE_REJECTED` event metadata is inspected
- THEN no metadata value MUST equal `sk-real-key`

#### Scenario: Rejected event metadata does not contain vault reference
- GIVEN a provider was constructed from `vault:openai_key` reference
- AND a `SET LLM` statement is rejected
- WHEN the `LLM_CONFIG_CHANGE_REJECTED` event metadata is inspected
- THEN no metadata value MUST equal `vault:openai_key`
