# Tasks: sdk-error-handling

## Implementation

- [x] Add `isRetryable()` method and `retryable` field to `SdkException` base class
- [x] Create `SdkConfigException` subclass (default retryable=false)
- [x] Create `SdkVaultException` subclass (default retryable=false)
- [x] Create `SdkLlmException` subclass (default retryable=true)
- [x] Create `SdkToolException` subclass (default retryable=false)
- [x] Create `SdkPermissionException` subclass (default retryable=false)
- [x] Refactor `SdkBuilder.build()` to throw `SdkConfigException` for `ConfigException` and `SdkVaultException` for vault errors
- [x] Refactor `SdkAgent.run()` to throw `SdkLlmException` for LLM errors and `SdkToolException` for tool errors

## Testing

- [x] Lint/validate: run `mvn compile -q` to verify compilation
- [x] Update `SdkExceptionTest` to cover `isRetryable()` on base class
- [x] Add unit tests for each subclass verifying constructors, `isRetryable()` default, override, and cause chain
- [x] Run unit tests: `mvn test -pl . -Dtest="org.specdriven.sdk.*Test"` to verify all SDK tests pass

## Verification

- [x] Verify all `catch (SdkException)` in existing test code still compiles and passes
- [x] Verify `SdkBuilder.build()` error paths throw typed subclasses
- [x] Verify `SdkAgent.run()` error paths throw typed subclasses
