package org.specdriven.agent.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.agent.vault.SecretVault;
import org.specdriven.agent.vault.VaultException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class ConfigLoaderVaultIntegrationTest {

    private final StubVault vault = new StubVault();

    // --- loadWithVault from filesystem ---

    @Test
    void loadWithVault_resolvesVaultReference(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, "llm:\n  apiKey: vault:openai_key\n");
        vault.store("openai_key", "sk-real-key");

        Map<String, String> result = ConfigLoader.loadWithVault(file, vault);
        assertEquals("sk-real-key", result.get("llm.apiKey"));
    }

    @Test
    void loadWithVault_mixedVaultAndPlainValues(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, "llm:\n  apiKey: vault:openai_key\n  model: gpt-4\n");
        vault.store("openai_key", "sk-real-key");

        Map<String, String> result = ConfigLoader.loadWithVault(file, vault);
        assertEquals("sk-real-key", result.get("llm.apiKey"));
        assertEquals("gpt-4", result.get("llm.model"));
    }

    @Test
    void loadWithVault_missingVaultKey_throwsVaultException(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, "llm:\n  apiKey: vault:nonexistent\n");

        assertThrows(VaultException.class, () -> ConfigLoader.loadWithVault(file, vault));
    }

    @Test
    void loadWithVault_noVaultReferences_returnsIdenticalMap(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, "llm:\n  model: gpt-4\n  timeout: 60\n");

        Map<String, String> result = ConfigLoader.loadWithVault(file, vault);
        Map<String, String> expected = ConfigLoader.load(file).asMap();
        assertEquals(expected, result);
    }

    @Test
    void loadWithVault_withEnvSubstitution_resolvesBoth(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, "llm:\n  apiKey: vault:openai_key\n  path: ${PATH}\n");
        vault.store("openai_key", "sk-real-key");

        Map<String, String> result = ConfigLoader.loadWithVault(file, vault, true);
        assertEquals("sk-real-key", result.get("llm.apiKey"));
        // PATH env var should be resolved (not left as ${PATH})
        assertNotNull(result.get("llm.path"));
        assertNotEquals("${PATH}", result.get("llm.path"));
    }

    // --- loadWithVaultClasspath ---

    @Test
    void loadWithVaultClasspath_resolvesVaultReference() {
        // test-config.yaml has llm.apiKey = vault:openai_key — we test resolution against it
        vault.store("openai_key", "sk-classpath-key");

        Map<String, String> result = ConfigLoader.loadWithVaultClasspath("config/vault-test-config.yaml", vault);
        assertEquals("sk-classpath-key", result.get("llm.apiKey"));
    }

    // --- Plain load() is unchanged ---

    @Test
    void plainLoad_doesNotResolveVaultReferences(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("config.yaml");
        Files.writeString(file, "llm:\n  apiKey: vault:openai_key\n");

        Config config = ConfigLoader.load(file);
        assertEquals("vault:openai_key", config.getString("llm.apiKey"));
    }

    // --- Helpers ---

    private static class StubVault implements SecretVault {
        private final Map<String, String> secrets = new HashMap<>();

        void store(String key, String value) {
            secrets.put(key, value);
        }

        @Override
        public String get(String key) {
            if (!secrets.containsKey(key)) {
                throw new VaultException("Secret not found: " + key);
            }
            return secrets.get(key);
        }

        @Override
        public void set(String key, String plaintext, String description) {
            secrets.put(key, plaintext);
        }

        @Override
        public void delete(String key) {
            secrets.remove(key);
        }

        @Override
        public List<org.specdriven.agent.vault.VaultEntry> list() {
            return List.of();
        }

        @Override
        public boolean exists(String key) {
            return secrets.containsKey(key);
        }
    }
}
