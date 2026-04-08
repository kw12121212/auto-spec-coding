package org.specdriven.agent.vault;

import java.time.Instant;

/**
 * Immutable record carrying metadata for a vault entry.
 * Does not expose the encrypted or decrypted value.
 */
public record VaultEntry(
    String key,
    Instant createdAt,
    String description
) {}
