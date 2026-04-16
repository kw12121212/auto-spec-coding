package org.specdriven.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    // --- Filesystem loading ---

    @Test
    void loadFromFilesystem_readsTopLevelKeys(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("test.yaml");
        Files.writeString(file, "name: hello\nport: 8080\n");
        Config config = ConfigLoader.load(file);
        assertEquals("hello", config.getString("name"));
        assertEquals(8080, config.getInt("port", 0));
    }

    @Test
    void loadFromFilesystem_missingFile_throwsConfigException() {
        assertThrows(ConfigException.class, () -> ConfigLoader.load(Path.of("/nonexistent/config.yaml")));
    }

    @Test
    void loadFromFilesystem_malformedYaml_throwsConfigException(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("bad.yaml");
        Files.writeString(file, "agent:\n  name: \"test\n  invalid [[\n");
        assertThrows(ConfigException.class, () -> ConfigLoader.load(file));
    }

    // --- Classpath loading ---

    @Test
    void loadFromClasspath_readsConfig() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertEquals("test-agent", config.getString("agent.name"));
    }

    @Test
    void loadFromClasspath_missingResource_throwsConfigException() {
        assertThrows(ConfigException.class, () -> ConfigLoader.loadClasspath("nonexistent.yaml"));
    }

    // --- Dot-notation nested access ---

    @Test
    void dotNotation_resolvesNestedKeys() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertEquals("openai", config.getString("llm.provider"));
        assertEquals("gpt-4", config.getString("llm.model"));
    }

    // --- getSection ---

    @Test
    void getSection_returnsScopedSubConfig() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        Config llm = config.getSection("llm");
        assertEquals("openai", llm.getString("provider"));
        assertEquals("gpt-4", llm.getString("model"));
    }

    @Test
    void getSection_missingSection_throwsConfigException() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertThrows(ConfigException.class, () -> config.getSection("nonexistent"));
    }

    // --- asMap flattening ---

    @Test
    void asMap_flattensToDotNotation() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        Map<String, String> flat = config.asMap();
        assertEquals("test-agent", flat.get("agent.name"));
        assertEquals("3", flat.get("agent.max_retries"));
        assertEquals("true", flat.get("agent.debug"));
        assertEquals("openai", flat.get("llm.provider"));
        assertEquals("ask", flat.get("permissions.default"));
    }

    @Test
    void asMap_isCompatibleWithAgentInit() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        Map<String, String> flat = config.asMap();
        // Verify it's a Map<String, String> — compatible with Agent.init(Map<String, String>)
        assertNotNull(flat);
        assertFalse(flat.isEmpty());
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
        }
    }

    // --- Typed accessors with defaults ---

    @Test
    void typedAccessors_returnDefaultsForMissingKeys() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertEquals("fallback", config.getString("missing.key", "fallback"));
        assertEquals(42, config.getInt("missing.key", 42));
        assertFalse(config.getBoolean("missing.key", false));
    }

    @Test
    void getString_requiredKey_throwsOnMissing() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertThrows(ConfigException.class, () -> config.getString("missing.key"));
    }

    @Test
    void getInt_parsesNumericValues() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertEquals(3, config.getInt("agent.max_retries", 0));
    }

    @Test
    void getBoolean_parsesBooleanValues() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        assertTrue(config.getBoolean("agent.debug", false));
    }

    // --- Environment variable substitution ---

    @Test
    void envVarSubstitution_resolvesKnownVar(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("env.yaml");
        Files.writeString(file, "key: ${PATH}\n");
        Config config = ConfigLoader.load(file, true);
        String value = config.getString("key");
        assertNotNull(value);
        assertNotEquals("${PATH}", value); // should be resolved
    }

    @Test
    void envVarSubstitution_leavesUnknownVarUnchanged(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("env.yaml");
        Files.writeString(file, "key: ${SURELY_UNDEFINED_VAR_XYZ_12345}\n");
        Config config = ConfigLoader.load(file, true);
        assertEquals("${SURELY_UNDEFINED_VAR_XYZ_12345}", config.getString("key"));
    }

    @Test
    void envVarSubstitution_offByDefault(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("env.yaml");
        Files.writeString(file, "key: ${PATH}\n");
        Config config = ConfigLoader.load(file);
        assertEquals("${PATH}", config.getString("key"));
    }

    @Test
    void environmentProfiles_preserveNestedSelections(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("profiles.yaml");
        Files.writeString(file, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      jdk:
                        javaHome: /opt/jdk-25
                      node:
                        nodeHome: /opt/node-22
                    ci:
                      go:
                        goRoot: /opt/go-1.23
                """);

        Config config = ConfigLoader.load(file);

        assertEquals("dev", config.getString("environmentProfiles.default"));
        assertEquals("/opt/jdk-25", config.getString("environmentProfiles.profiles.dev.jdk.javaHome"));
        assertEquals("/opt/node-22", config.getString("environmentProfiles.profiles.dev.node.nodeHome"));
        assertEquals("/opt/go-1.23", config.getString("environmentProfiles.profiles.ci.go.goRoot"));
    }

    @Test
    void environmentProfiles_missingDefaultReference_throwsConfigException(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("profiles.yaml");
        Files.writeString(file, """
                environmentProfiles:
                  default: prod
                  profiles:
                    dev:
                      jdk:
                        javaHome: /opt/jdk-25
                """);

        ConfigException ex = assertThrows(ConfigException.class, () -> ConfigLoader.load(file));

        assertTrue(ex.getMessage().contains("Default environment profile 'prod'"));
    }

    @Test
    void environmentProfiles_invalidFieldValue_throwsConfigException(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("profiles.yaml");
        Files.writeString(file, """
                environmentProfiles:
                  default: dev
                  profiles:
                    dev:
                      jdk:
                        javaHome:
                          nested: invalid
                """);

        ConfigException ex = assertThrows(ConfigException.class, () -> ConfigLoader.load(file));

        assertTrue(ex.getMessage().contains("environmentProfiles.profiles.dev.jdk.javaHome"));
    }

    // --- Immutability ---

    @Test
    void asMap_isImmutable() {
        Config config = ConfigLoader.loadClasspath("config/test-config.yaml");
        Map<String, String> flat = config.asMap();
        assertThrows(UnsupportedOperationException.class, () -> flat.put("new", "value"));
    }
}
