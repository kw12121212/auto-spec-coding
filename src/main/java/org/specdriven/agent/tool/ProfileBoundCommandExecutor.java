package org.specdriven.agent.tool;

import org.specdriven.agent.agent.DefaultLlmProviderRegistry;
import org.specdriven.sdk.LealonePlatform;
import org.specdriven.sdk.SpecDriven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes commands through the repository's configured environment-profile binding when available.
 */
@FunctionalInterface
public interface ProfileBoundCommandExecutor {

    ProfileBoundCommandExecutor DEFAULT = new DefaultProfileBoundCommandExecutor();
    String CONFIG_PATH_KEY = "specdriven.agent.config";
    String CONFIG_PATH_ENV = "SPEC_DRIVEN_AGENT_CONFIG";

    Optional<ExecutionResult> execute(Path projectRoot,
                                      Map<String, String> executionConfig,
                                      String requestedProfile,
                                      List<String> command) throws Exception;

    record ExecutionResult(String resolvedProfile, List<String> command, int exitCode, String stdout, String stderr) {
        public ExecutionResult {
            if (resolvedProfile == null || resolvedProfile.isBlank()) {
                throw new IllegalArgumentException("resolvedProfile must not be null or blank");
            }
            command = List.copyOf(Objects.requireNonNull(command, "command must not be null"));
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
        }
    }
}

final class DefaultProfileBoundCommandExecutor implements ProfileBoundCommandExecutor {

    @Override
    public Optional<ExecutionResult> execute(Path projectRoot,
                                             Map<String, String> executionConfig,
                                             String requestedProfile,
                                             List<String> command) throws Exception {
        Objects.requireNonNull(projectRoot, "projectRoot must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Path configPath = resolveConfigPath(projectRoot, executionConfig);
        if (configPath == null) {
            if (requestedProfile != null && !requestedProfile.isBlank()) {
                throw new IllegalStateException(
                        "No repository config path is available for requested profile '" + requestedProfile + "'");
            }
            return Optional.empty();
        }

        try (SpecDriven sdk = SpecDriven.builder()
                .config(configPath)
                .providerRegistry(new DefaultLlmProviderRegistry())
                .build()) {
            LealonePlatform.SandlockExecutionResult result = requestedProfile == null || requestedProfile.isBlank()
                    ? sdk.platform().sandlock().execute(command)
                    : sdk.platform().sandlock().execute(requestedProfile, command);
            return Optional.of(new ExecutionResult(
                    result.resolvedProfile(),
                    result.command(),
                    result.exitCode(),
                    result.stdout(),
                    result.stderr()));
        } catch (LealonePlatform.SandlockExecutionException e) {
            if ((requestedProfile == null || requestedProfile.isBlank())
                    && e.failureCode() == LealonePlatform.SandlockFailureCode.NO_EFFECTIVE_PROFILE) {
                return Optional.empty();
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Path resolveConfigPath(Path projectRoot, Map<String, String> executionConfig) {
        if (executionConfig != null) {
            String inlineConfigPath = trimToNull(executionConfig.get(CONFIG_PATH_KEY));
            if (inlineConfigPath != null) {
                return Path.of(inlineConfigPath).toAbsolutePath().normalize();
            }
        }

        String envConfigPath = trimToNull(System.getenv(ProfileBoundCommandExecutor.CONFIG_PATH_ENV));
        if (envConfigPath != null) {
            return Path.of(envConfigPath).toAbsolutePath().normalize();
        }

        String propertyConfigPath = trimToNull(System.getProperty(CONFIG_PATH_KEY));
        if (propertyConfigPath != null) {
            return Path.of(propertyConfigPath).toAbsolutePath().normalize();
        }

        Path repoConfig = projectRoot.toAbsolutePath().normalize().resolve("agent.yaml");
        return Files.isRegularFile(repoConfig) ? repoConfig : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
