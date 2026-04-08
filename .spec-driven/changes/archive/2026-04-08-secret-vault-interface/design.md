# Design: secret-vault-interface

## Approach

Define interfaces and data types in a new `org.specdriven.agent.vault` package. The design separates the vault contract from any storage implementation — the interface only describes what operations are available, not how or where secrets are stored.

### SecretVault Interface

```java
public interface SecretVault {
    String get(String key);
    void set(String key, String plaintext, String description);
    void delete(String key);
    List<VaultEntry> list();
    boolean exists(String key);
}
```

- `get(key)` returns the decrypted plaintext or throws `VaultException` if not found or decryption fails.
- `set(key, plaintext, description)` encrypts and stores. If key exists, overwrites (rotation).
- `list()` returns all entries with metadata, but **not** decrypted values.
- `exists(key)` checks presence without decryption.

### VaultEntry Record

```java
public record VaultEntry(
    String key,
    Instant createdAt,
    String description
) {}
```

Carries metadata only. The encrypted value is an implementation detail not exposed at the interface level.

### VaultResolver

A static utility that scans a `Map<String, String>` for values matching `vault:key_name` and replaces them with decrypted values from a `SecretVault` instance.

```java
public final class VaultResolver {
    public static Map<String, String> resolve(Map<String, String> config, SecretVault vault);
}
```

### Development Default Key

When `SPEC_DRIVEN_MASTER_KEY` is not set, a fixed default key is used. This default key MUST NOT be used in production — the `VaultResolver` and vault implementations SHOULD log a warning when the default key is active. The default key is a hardcoded string constant.

### VaultException

Extends `RuntimeException`, carries a descriptive message and optional cause.

## Key Decisions

1. **Random nonce per encryption** — Each `set()` call generates a fresh 12-byte random nonce, stored alongside the ciphertext. No nonce-reuse risk.
2. **Fixed development master key** — Developers don't need to set `SPEC_DRIVEN_MASTER_KEY` locally. A warning is logged when the default is active. Production must set the env var.
3. **Metadata in VaultEntry** — Creation timestamp and description per entry for audit. Rotation hints deferred to future work.
4. **Interface-only, no storage** — This change defines the contract only. `lealone-vault-impl` provides the Lealone DB storage.

## Alternatives Considered

- **Deterministic nonce from key name** — Risked nonce reuse on key name collisions. Rejected in favor of random nonce.
- **VaultEntry carries decrypted value** — Would require all `list()` callers to trigger decryption. Metadata-only is safer and sufficient for listing/display.
- **VaultResolver as ConfigLoader decorator** — Would couple vault to the config loading path. A standalone resolver is more flexible and testable.
