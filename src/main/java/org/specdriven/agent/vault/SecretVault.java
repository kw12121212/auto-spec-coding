package org.specdriven.agent.vault;

import java.util.List;

/**
 * Interface for encrypted secret storage and retrieval.
 * Implementations handle encryption details and persistence;
 * this contract only describes the operations available.
 */
public interface SecretVault {

    /**
     * Retrieve and decrypt the secret identified by the given key.
     *
     * @param key the secret key
     * @return the decrypted plaintext value
     * @throws VaultException if the key is not found or decryption fails
     */
    String get(String key);

    /**
     * Encrypt and store a secret. If the key already exists, it is overwritten (rotation).
     *
     * @param key         the secret key
     * @param plaintext   the plaintext value to encrypt and store
     * @param description a human-readable description for audit
     */
    void set(String key, String plaintext, String description);

    /**
     * Delete a secret. Idempotent — no error if the key does not exist.
     *
     * @param key the secret key
     */
    void delete(String key);

    /**
     * List all vault entries with metadata. Does not include decrypted values.
     *
     * @return list of entries with metadata
     */
    List<VaultEntry> list();

    /**
     * Check whether a secret exists without triggering decryption.
     *
     * @param key the secret key
     * @return true if the key exists, false otherwise
     */
    boolean exists(String key);
}
