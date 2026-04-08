# Tasks: vault-config-integration

## Implementation

- [x] Add `loadWithVault(Path, SecretVault)` method to ConfigLoader — chains load → asMap → VaultResolver.resolve
- [x] Add `loadWithVaultClasspath(String, SecretVault)` method to ConfigLoader — same chain for classpath resources
- [x] Add overloaded variants with `enableEnvSubstitution` boolean parameter for env-var + vault chaining
- [x] Create `VaultFactory` utility class with `create(EventBus)` and `create(EventBus, String jdbcUrl)` methods
- [x] Update `ConfigLoader` to import vault package types (VaultResolver, SecretVault, VaultException)

## Testing

- [x] Lint: run `mvn compile -q` to verify all new code compiles without errors
- [x] Write unit test: `ConfigLoaderVaultIntegrationTest` — test vault reference resolution, passthrough, missing key error, mixed values
- [x] Write unit test: `VaultFactoryTest` — test default creation and custom JDBC URL creation
- [x] Unit tests: run `mvn test -pl . -Dtest="ConfigLoaderVaultIntegrationTest,VaultFactoryTest" -q`

## Verification

- [x] Verify existing ConfigLoader tests still pass (`mvn test -pl . -Dtest="ConfigLoaderTest"`)
- [x] Verify existing VaultResolver tests still pass (`mvn test -pl . -Dtest="VaultResolverTest"`)
- [x] Verify no `vault:` prefix behavior when using plain `ConfigLoader.load()` (no regression)
