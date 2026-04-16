package org.specdriven.agent.config;

import org.specdriven.agent.vault.SecretVault;
import org.specdriven.agent.vault.VaultResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry point for loading YAML configuration files.
 * Supports filesystem paths and classpath resources, with optional env-var substitution.
 */
public final class ConfigLoader {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Set<String> ENVIRONMENT_PROFILE_KEYS = Set.of("default", "profiles");
    private static final Set<String> SUPPORTED_PROFILE_FAMILIES = Set.of("jdk", "node", "go", "python");

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
        validateEnvironmentProfiles(data, path.toString());
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
        validateEnvironmentProfiles(data, "classpath:" + resource);
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
        Map<String, Object> result = new LinkedHashMap<>();
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

    private static void validateEnvironmentProfiles(Map<String, Object> data, String sourceDescription) {
        Object rawProfilesSection = data.get("environmentProfiles");
        if (rawProfilesSection == null) {
            return;
        }
        if (!(rawProfilesSection instanceof Map<?, ?> profilesSection)) {
            throw new ConfigException("Config key 'environmentProfiles' is not a section in " + sourceDescription);
        }
        for (Object key : profilesSection.keySet()) {
            if (!(key instanceof String keyName) || !ENVIRONMENT_PROFILE_KEYS.contains(keyName)) {
                throw new ConfigException("Unsupported environment profile key 'environmentProfiles."
                        + key + "' in " + sourceDescription);
            }
        }
        String defaultProfile = requireNonBlankString(profilesSection.get("default"),
                "environmentProfiles.default", sourceDescription);
        Object rawDeclaredProfiles = profilesSection.get("profiles");
        if (!(rawDeclaredProfiles instanceof Map<?, ?> declaredProfiles) || declaredProfiles.isEmpty()) {
            throw new ConfigException("Config section 'environmentProfiles.profiles' must declare at least one named"
                    + " profile in " + sourceDescription);
        }
        if (!declaredProfiles.containsKey(defaultProfile)) {
            throw new ConfigException("Default environment profile '" + defaultProfile
                    + "' does not match any declared profile in " + sourceDescription);
        }
        for (Map.Entry<?, ?> profileEntry : declaredProfiles.entrySet()) {
            if (!(profileEntry.getKey() instanceof String profileName) || profileName.isBlank()) {
                throw new ConfigException("Environment profile names must be non-blank strings in "
                        + sourceDescription);
            }
            if (!(profileEntry.getValue() instanceof Map<?, ?> profileBody)) {
                throw new ConfigException("Environment profile '" + profileName
                        + "' must be a section in " + sourceDescription);
            }
            validateProfileFamilies(profileName, profileBody, sourceDescription);
        }
    }

    private static void validateProfileFamilies(String profileName, Map<?, ?> profileBody, String sourceDescription) {
        for (Map.Entry<?, ?> familyEntry : profileBody.entrySet()) {
            if (!(familyEntry.getKey() instanceof String familyName) || familyName.isBlank()) {
                throw new ConfigException("Environment profile '" + profileName
                        + "' contains a blank toolchain family key in " + sourceDescription);
            }
            if (!SUPPORTED_PROFILE_FAMILIES.contains(familyName)) {
                throw new ConfigException("Unsupported environment profile key 'environmentProfiles.profiles."
                        + profileName + "." + familyName + "' in " + sourceDescription);
            }
            if (!(familyEntry.getValue() instanceof Map<?, ?> familyConfig)) {
                throw new ConfigException("Environment profile family 'environmentProfiles.profiles."
                        + profileName + "." + familyName + "' must be a section in " + sourceDescription);
            }
            validateProfileFields(profileName, familyName, familyConfig, sourceDescription);
        }
    }

    private static void validateProfileFields(String profileName,
                                              String familyName,
                                              Map<?, ?> familyConfig,
                                              String sourceDescription) {
        for (Map.Entry<?, ?> fieldEntry : familyConfig.entrySet()) {
            if (!(fieldEntry.getKey() instanceof String fieldName) || fieldName.isBlank()) {
                throw new ConfigException("Environment profile family 'environmentProfiles.profiles."
                        + profileName + "." + familyName + "' contains a blank field name in "
                        + sourceDescription);
            }
            Object value = fieldEntry.getValue();
            if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw new ConfigException("Invalid environment profile field 'environmentProfiles.profiles."
                        + profileName + "." + familyName + "." + fieldName + "' in " + sourceDescription);
            }
        }
    }

    private static String requireNonBlankString(Object value, String key, String sourceDescription) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new ConfigException("Config key '" + key + "' must be a non-blank string in "
                    + sourceDescription);
        }
        return stringValue;
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
