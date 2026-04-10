package org.specdriven.agent.config;

import org.specdriven.agent.vault.SecretVault;
import org.specdriven.agent.vault.VaultResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry point for loading YAML configuration files.
 * Supports filesystem paths and classpath resources, with optional env-var substitution.
 */
public final class ConfigLoader {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {}

    /**
     * Load config from a filesystem path, without env-var substitution.
     */
    public static Config load(Path path) {
        return load(path, false);
    }

    /**
     * Load config from a filesystem path, with optional env-var substitution.
     */
    @SuppressWarnings("unchecked")
    public static Config load(Path path, boolean substituteEnvVars) {
        if (!Files.exists(path)) {
            throw new ConfigException("Config file not found: " + path.toAbsolutePath());
        }
        Map<String, Object> data;
        try (InputStream is = Files.newInputStream(path)) {
            data = YAML_MAPPER.readValue(is, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ConfigException("Failed to read config file: " + path.toAbsolutePath(), e);
        }
        if (data == null) {
            throw new ConfigException("Config file is empty: " + path.toAbsolutePath());
        }
        if (substituteEnvVars) {
            data = substituteEnvVars(data);
        }
        return new Config(data, path.toString());
    }

    /**
     * Load config from a classpath resource, without env-var substitution.
     */
    public static Config loadClasspath(String resource) {
        return loadClasspath(resource, false);
    }

    /**
     * Load config from a classpath resource, with optional env-var substitution.
     */
    @SuppressWarnings("unchecked")
    public static Config loadClasspath(String resource, boolean substituteEnvVars) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (is == null) {
            throw new ConfigException("Classpath resource not found: " + resource);
        }
        Map<String, Object> data;
        try (is) {
            data = YAML_MAPPER.readValue(is, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ConfigException("Failed to read classpath resource: " + resource, e);
        }
        if (data == null) {
            throw new ConfigException("Classpath resource is empty: " + resource);
        }
        if (substituteEnvVars) {
            data = substituteEnvVars(data);
        }
        return new Config(data, "classpath:" + resource);
    }

    // --- Vault-aware loading ---

    /**
     * Load config from a filesystem path and resolve {@code vault:} references.
     */
    public static Map<String, String> loadWithVault(Path path, SecretVault vault) {
        return loadWithVault(path, vault, false);
    }

    /**
     * Load config from a filesystem path with optional env-var substitution and vault resolution.
     */
    public static Map<String, String> loadWithVault(Path path, SecretVault vault, boolean substituteEnvVars) {
        Config config = load(path, substituteEnvVars);
        return VaultResolver.resolve(config.asMap(), vault);
    }

    /**
     * Load config from a classpath resource and resolve {@code vault:} references.
     */
    public static Map<String, String> loadWithVaultClasspath(String resource, SecretVault vault) {
        return loadWithVaultClasspath(resource, vault, false);
    }

    /**
     * Load config from a classpath resource with optional env-var substitution and vault resolution.
     */
    public static Map<String, String> loadWithVaultClasspath(String resource, SecretVault vault, boolean substituteEnvVars) {
        Config config = loadClasspath(resource, substituteEnvVars);
        return VaultResolver.resolve(config.asMap(), vault);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> substituteEnvVars(Map<String, Object> data) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                result.put(entry.getKey(), substituteString(s));
            } else if (value instanceof Map) {
                result.put(entry.getKey(), substituteEnvVars((Map<String, Object>) value));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static String substituteString(String value) {
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String envVar = System.getenv(matcher.group(1));
            matcher.appendReplacement(sb, envVar != null ? Matcher.quoteReplacement(envVar) : Matcher.quoteReplacement(matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
