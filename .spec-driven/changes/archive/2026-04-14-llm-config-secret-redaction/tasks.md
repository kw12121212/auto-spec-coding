# Tasks: llm-config-secret-redaction

## Implementation

- [x] Override `LlmConfig.toString()` to replace `apiKey` with fixed `***` placeholder
- [x] Audit `LlmConfig` compact constructor exception messages for apiKey leakage; fix if needed
- [x] Audit `VaultResolver.resolve()` exception path: ensure `VaultException` messages do not include successfully resolved secret values from the same batch
- [x] Add defensive guard in `DefaultLlmProviderRegistry.publishConfigChangedEvent()` to assert metadata values do not contain the current provider's resolved API key or vault reference
- [x] Audit `SetLlmSqlException` messages in `DefaultLlmProviderRegistry` for secret leakage; fix if needed

## Testing

- [x] Lint: `mvn compile -pl . -q` — compile check after changes
- [x] Add `LlmConfigTest` test: `toString()` does not contain the API key value
- [x] Add `LlmConfigTest` test: `toString()` contains non-sensitive fields (baseUrl, model, timeout, maxRetries)
- [x] Add `LlmConfigTest` test: constructor exception for blank apiKey does not echo the input value
- [x] Add `DefaultLlmProviderRegistryTest` test: `LLM_CONFIG_CHANGED` event metadata does not contain the provider's resolved API key
- [x] Add `VaultResolverTest` test: partial resolution failure exception does not leak other resolved values
- [x] `mvn test -pl . -q` — full unit test suite passes

## Verification

- [x] `grep -r "apiKey" src/main/java/ | grep -i "toString\|getMessage\|getLocalizedMessage"` returns no results showing secret in string output
- [x] Verify all new tests pass independently
- [x] Verify existing tests continue to pass unchanged
