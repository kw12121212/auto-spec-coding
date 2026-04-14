package org.specdriven.agent.vault;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaultResolverTest {

    private final StubVault vault = new StubVault();

    @Test
    void resolveVaultReference() {
        vault.store.put("openai_key", "sk-real-key");
        Map<String, String> config = Map.of("llm.apiKey", "vault:openai_key");

        Map<String, String> resolved = VaultResolver.resolve(config, vault);

        assertEquals("sk-real-key", resolved.get("llm.apiKey"));
    }

    @Test
    void passThroughNonVaultValues() {
        vault.store.put("openai_key", "sk-real-key");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("llm.model", "gpt-4");
        config.put("llm.apiKey", "vault:openai_key");

        Map<String, String> resolved = VaultResolver.resolve(config, vault);

        assertEquals("gpt-4", resolved.get("llm.model"));
        assertEquals("sk-real-key", resolved.get("llm.apiKey"));
    }

    @Test
    void missingVaultKeyThrows() {
        Map<String, String> config = Map.of("llm.apiKey", "vault:nonexistent");

        assertThrows(VaultException.class, () -> VaultResolver.resolve(config, vault));
    }

    @Test
    void emptyConfig() {
        Map<String, String> resolved = VaultResolver.resolve(Map.of(), vault);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void noVaultReferences() {
        Map<String, String> config = Map.of("llm.model", "gpt-4", "llm.timeout", "30");
        Map<String, String> resolved = VaultResolver.resolve(config, vault);

        assertEquals(config, resolved);
    }

    @Test
    void nullValuePassthrough() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("key", null);

        Map<String, String> resolved = VaultResolver.resolve(config, vault);
        assertNull(resolved.get("key"));
    }

    @Test
    void partialResolutionFailure_doesNotLeakOtherResolvedValues() {
        vault.store.put("existing_key", "sk-real-key");
        Map<String, String> config = new LinkedHashMap<>();
        config.put("apiKey", "vault:existing_key");
        config.put("secret2", "vault:missing_key");

        VaultException ex = assertThrows(VaultException.class,
                () -> VaultResolver.resolve(config, vault));
        String msg = ex.getMessage();
        assertFalse(msg.contains("sk-real-key"),
                "Exception message must not leak other resolved secret values");
        assertTrue(msg.contains("missing_key"),
                "Exception message must identify the missing key");
    }

    private static class StubVault implements SecretVault {
        final Map<String, String> store = new LinkedHashMap<>();

        @Override
        public String get(String key) {
            String v = store.get(key);
            if (v == null) throw new VaultException("Secret not found: " + key);
            return v;
        }

        @Override
        public void set(String key, String plaintext, String description) {
            store.put(key, plaintext);
        }

        @Override
        public void delete(String key) { store.remove(key); }

        @Override
        public java.util.List<VaultEntry> list() {
            return store.keySet().stream()
                    .map(k -> new VaultEntry(k, Instant.now(), ""))
                    .toList();
        }

        @Override
        public boolean exists(String key) { return store.containsKey(key); }
    }
}
