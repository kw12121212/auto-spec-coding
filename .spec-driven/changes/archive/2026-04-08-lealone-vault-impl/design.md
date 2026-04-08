# Design: lealone-vault-impl

## Approach

Follow the established Lealone store pattern (LealoneTaskStore, LealoneTeamStore, LealoneCronStore) — constructor takes `EventBus` and JDBC URL, creates tables in `initTables()`, uses per-operation `DriverManager.getConnection()` with try-with-resources.

**Encryption**: Use JDK `javax.crypto.Cipher` with `AES/GCM/NoPadding`. Derive a 256-bit AES key from `VaultMasterKey.get()` by SHA-256 hashing the master key string. Generate a random 12-byte IV per encryption operation (stored alongside ciphertext). Produce 128-bit GCM authentication tag.

**Storage**: Two Lealone tables:
- `vault_secrets` — stores encrypted entries (key, encrypted_value, iv, description, created_at, updated_at)
- `vault_audit_log` — stores audit trail (id, operation, vault_key, event_ts)

**Audit**: Publish events via `EventBus` on set/delete operations. Use new `EventType` values `VAULT_SECRET_CREATED` and `VAULT_SECRET_DELETED`. The existing `LealoneAuditLogStore` (subscribed to all event types) will capture these automatically.

## Key Decisions

1. **SHA-256 to derive AES key from master key string** — The master key is an arbitrary-length string from an env var. SHA-256 produces exactly 256 bits for AES-256. Simple, no KDF overhead. Acceptable because master key entropy is user-controlled.

2. **Random 12-byte IV per encryption, stored in DB row** — GCM requires unique IV per key. Storing IV alongside ciphertext is standard practice. Random generation (not counter-based) avoids coordination overhead in embedded single-process use.

3. **Separate `vault_audit_log` table rather than only EventBus** — EventBus is fire-and-forget; if no subscriber is registered, events are lost. A dedicated table ensures vault operations are always auditable even without an active `LealoneAuditLogStore`.

4. **VARCHAR columns for encrypted data** — Store ciphertext and IV as hex-encoded VARCHAR rather than BLOB, consistent with the project's pattern of keeping table schemas simple and debuggable.

## Alternatives Considered

- **PBKDF2/HKDF for key derivation** — More standard for password-based derivation, but overkill here: the master key is already a high-entropy string, not a user password. SHA-256 is sufficient.
- **Counter-based IV** — Would avoid storing IV, but requires persisting a counter and handling resets. Random IV is simpler for an embedded single-process vault.
- **No dedicated audit table (rely only on EventBus)** — Rejected because vault operations are security-sensitive and must always be auditable, even if no EventBus subscriber is configured.
