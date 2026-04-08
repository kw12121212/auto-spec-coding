# sdk-public-api.md

## MODIFIED Requirements

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
