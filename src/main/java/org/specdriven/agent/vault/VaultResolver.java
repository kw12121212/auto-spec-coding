package org.specdriven.agent.vault;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves {@code vault:key_name} references in config maps to decrypted plaintext values.
 * Non-vault values are passed through unchanged.
 */
public final class VaultResolver {

    private static final String VAULT_PREFIX = "vault:";

    private VaultResolver() {}

    /**
     * Scan the config map for values matching {@code vault:key_name} and replace them
     * with decrypted values from the given vault.
     *
     * @param config the config map to resolve
     * @param vault  the vault to read secrets from
     * @return a new map with vault references replaced by plaintext values
     * @throws VaultException if a referenced vault key is not found
     */
    public static Map<String, String> resolve(Map<String, String> config, SecretVault vault) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.startsWith(VAULT_PREFIX)) {
                String vaultKey = value.substring(VAULT_PREFIX.length());
                result.put(entry.getKey(), vault.get(vaultKey));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
}
