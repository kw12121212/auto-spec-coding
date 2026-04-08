# Design: vault-config-integration

## Approach

Add vault-aware overloads to `ConfigLoader` that accept a `SecretVault` parameter. The new methods chain the existing `load()` → `Config.asMap()` → `VaultResolver.resolve(configMap, vault)` pipeline, returning the fully resolved flat map. This keeps ConfigLoader as the single entry point for all config loading without modifying the AgentContext or Config interfaces.

### Call chain

```
ConfigLoader.loadWithVault(path, vault)
  → load(path)                    // existing: YAML → Config
  → config.asMap()                // existing: nested → flat Map<String, String>
  → VaultResolver.resolve(map, vault)  // existing: vault: refs → plaintext
  → return resolved map
```

### Integration point

The vault resolution happens at the ConfigLoader level, not inside AgentContext or Config. This is because:
1. ConfigLoader is already responsible for env-var substitution — vault resolution is the same class of transform
2. AgentContext is a simple context holder and should not orchestrate infrastructure
3. Callers who need raw config (no vault) can still use the existing `load()` methods

## Key Decisions

- **ConfigLoader-level integration**: Vault resolution is a config loading concern, not a context concern. Callers explicitly opt in by using `loadWithVault()`.
- **Ordering**: Env-var substitution runs first (inside Config), vault resolution runs second (on the flat map). This allows `vault:${KEY_NAME}` patterns where the env-var determines which vault key to resolve.
- **No AgentContext changes**: AgentContext already takes `Map<String, String>` — the resolved map flows in naturally.
- **VaultFactory utility**: Add a `VaultFactory.create()` convenience method that creates a LealoneVault from defaults (JDBC URL from config or hardcoded default, master key from VaultMasterKey).

## Alternatives Considered

- **AgentContext-level resolution**: Would require AgentContext to know about vault, violating its simple context-holder role. Rejected.
- **Config.asMapWithVault(vault)**: Would add a vault dependency to the Config record, which currently has no infrastructure dependencies. Rejected.
- **Auto-resolution on any `vault:` prefix during Config construction**: Too implicit — callers should opt in to vault resolution explicitly. Rejected.
