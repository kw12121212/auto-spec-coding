package org.specdriven.sdk;

import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.llm.RuntimeLlmConfigStore;
import org.specdriven.agent.loop.InteractiveSessionFactory;
import org.specdriven.skill.compiler.ClassCacheManager;
import org.specdriven.skill.compiler.SkillSourceCompiler;
import org.specdriven.skill.hotload.SkillHotLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Public platform-level entry point for assembled Lealone-centered capabilities.
 */
public final class LealonePlatform implements AutoCloseable {

    static final String DEFAULT_JDBC_URL = "jdbc:lealone:embed:agent_db";

    private final DatabaseCapability database;
    private final LlmCapability llm;
    private final CompilerCapability compiler;
    private final InteractiveCapability interactive;
    private final SandlockCapability sandlock;
    private final EventBus eventBus;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // Metric counters
    private final AtomicLong compilationOps = new AtomicLong();
    private final AtomicLong llmCacheHits = new AtomicLong();
    private final AtomicLong llmCacheMisses = new AtomicLong();
    private final AtomicLong toolCacheHits = new AtomicLong();
    private final AtomicLong toolCacheMisses = new AtomicLong();
    private final AtomicLong interactionCount = new AtomicLong();

    // Metric-accumulation EventBus subscriptions (stored for cleanup in stop())
    private final List<ConsumerRegistration> metricSubscriptions = new ArrayList<>();

    LealonePlatform(
            DatabaseCapability database,
            LlmCapability llm,
            CompilerCapability compiler,
            InteractiveCapability interactive,
            SandlockCapability sandlock,
            EventBus eventBus) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.llm = Objects.requireNonNull(llm, "llm must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.interactive = Objects.requireNonNull(interactive, "interactive must not be null");
        this.sandlock = Objects.requireNonNull(sandlock, "sandlock must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    }

    LealonePlatform(
            DatabaseCapability database,
            LlmCapability llm,
            CompilerCapability compiler,
            InteractiveCapability interactive,
            EventBus eventBus) {
        this(database, llm, compiler, interactive, new SandlockCapability(
                new SystemSandlockRuntime(), Map.of(), null), eventBus);
    }

    /**
     * Returns a builder that can assemble either the SDK agent facade or the platform surface.
     */
    public static SdkBuilder builder() {
        return new SdkBuilder();
    }

    public DatabaseCapability database() {
        return database;
    }

    public LlmCapability llm() {
        return llm;
    }

    public CompilerCapability compiler() {
        return compiler;
    }

    public InteractiveCapability interactive() {
        return interactive;
    }

    public SandlockCapability sandlock() {
        return sandlock;
    }

    /**
     * Applies a supported declarative service application entry from a readable {@code services.sql}
     * file using this platform's assembled JDBC runtime.
     */
    public ServiceApplicationBootstrapResult bootstrapServices(Path servicesSqlPath) {
        Objects.requireNonNull(servicesSqlPath, "servicesSqlPath must not be null");
        Path normalizedPath = servicesSqlPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException("services.sql must reference an existing file: " + normalizedPath);
        }
        if (!"services.sql".equals(normalizedPath.getFileName().toString())) {
            throw new IllegalArgumentException("bootstrap currently supports only files named services.sql: " + normalizedPath);
        }

        List<String> statements;
        try {
            String content = Files.readString(normalizedPath, StandardCharsets.UTF_8);
            statements = parseSupportedBootstrapStatements(content);
        } catch (BootstrapValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read services.sql: " + normalizedPath, e);
        }

        int appliedStatements = 0;
        try (Connection conn = DriverManager.getConnection(database.jdbcUrl(), "root", "");
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
                appliedStatements++;
            }
            return new ServiceApplicationBootstrapResult(normalizedPath, appliedStatements, List.copyOf(statements));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bootstrap services.sql against platform JDBC runtime", e);
        }
    }

    /**
     * Records the platform as running and registers EventBus subscriptions for metric accumulation.
     * Safe to call multiple times (idempotent).
     */
    public void start() {
        if (!started.compareAndSet(false, true)) return;
        registerMetricSubscription(EventType.SKILL_HOT_LOAD_OPERATION, e -> compilationOps.incrementAndGet());
        registerMetricSubscription(EventType.INTERACTIVE_COMMAND_HANDLED, e -> interactionCount.incrementAndGet());
        registerMetricSubscription(EventType.LLM_CACHE_HIT, e -> llmCacheHits.incrementAndGet());
        registerMetricSubscription(EventType.LLM_CACHE_MISS, e -> llmCacheMisses.incrementAndGet());
        registerMetricSubscription(EventType.TOOL_CACHE_HIT, e -> toolCacheHits.incrementAndGet());
        registerMetricSubscription(EventType.TOOL_CACHE_MISS, e -> toolCacheMisses.incrementAndGet());
    }

    private void registerMetricSubscription(EventType type, Consumer<Event> listener) {
        eventBus.subscribe(type, listener);
        metricSubscriptions.add(new ConsumerRegistration(type, listener));
    }

    /**
     * Tears down all capability domains in reverse dependency order with per-subsystem
     * exception suppression. Also unsubscribes metric-accumulation EventBus listeners.
     * Safe to call multiple times (idempotent).
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        // Unsubscribe metric listeners
        for (ConsumerRegistration reg : metricSubscriptions) {
            try {
                eventBus.unsubscribe(reg.type(), reg.listener());
            } catch (Exception ignored) {
            }
        }
        metricSubscriptions.clear();
        // Interactive (no explicit teardown needed beyond session GC)
        // Compiler (no explicit teardown needed)
        // LLM
        try {
            llm.providerRegistry().close();
        } catch (Exception ignored) {
        }
        llm.runtimeConfigStore().ifPresent(store -> {
            try {
                store.close();
            } catch (Exception ignored) {
            }
        });
        // DB (no explicit teardown needed for embedded Lealone)
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Synchronously probes each capability domain and returns an aggregated health result.
     * Publishes a {@code PLATFORM_HEALTH_CHECKED} event after the probes complete.
     */
    public PlatformHealth checkHealth() {
        long start = System.currentTimeMillis();
        List<SubsystemHealth> subsystems = new ArrayList<>(4);
        subsystems.add(probeDb());
        subsystems.add(probeLlm());
        subsystems.add(probeCompiler());
        subsystems.add(probeAgent());
        long probedAt = System.currentTimeMillis();
        PlatformHealth health = PlatformHealth.of(subsystems, probedAt);

        long durationMs = probedAt - start;
        eventBus.publish(new Event(
                EventType.PLATFORM_HEALTH_CHECKED,
                probedAt,
                "platform",
                Map.of("overallStatus", health.overallStatus().name(), "probeDurationMs", durationMs)));
        return health;
    }

    /**
     * Returns a snapshot of cumulative metric counters accumulated since the last {@code start()}.
     * Publishes a {@code PLATFORM_METRICS_SNAPSHOT} event.
     */
    public PlatformMetrics metrics() {
        long snapshotAt = System.currentTimeMillis();
        PlatformMetrics snapshot = new PlatformMetrics(
                0L, // promptTokens: no dedicated event emitted yet
                0L, // completionTokens: no dedicated event emitted yet
                compilationOps.get(),
                llmCacheHits.get(),
                llmCacheMisses.get(),
                toolCacheHits.get(),
                toolCacheMisses.get(),
                interactionCount.get(),
                snapshotAt);

        eventBus.publish(new Event(
                EventType.PLATFORM_METRICS_SNAPSHOT,
                snapshotAt,
                "platform",
                Map.of(
                        "compilationOps", snapshot.compilationOps(),
                        "llmCacheHits", snapshot.llmCacheHits(),
                        "llmCacheMisses", snapshot.llmCacheMisses(),
                        "toolCacheHits", snapshot.toolCacheHits(),
                        "toolCacheMisses", snapshot.toolCacheMisses(),
                        "interactionCount", snapshot.interactionCount())));
        return snapshot;
    }

    // --- Health probes ---

    private SubsystemHealth probeDb() {
        try (Connection conn = DriverManager.getConnection(database.jdbcUrl(), "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            if (rs.next()) {
                return SubsystemHealth.up("db");
            }
            return SubsystemHealth.down("db", "SELECT 1 returned no rows");
        } catch (Exception e) {
            return SubsystemHealth.down("db", e.getMessage());
        }
    }

    private SubsystemHealth probeLlm() {
        try {
            if (llm.providerRegistry().providerNames().isEmpty()) {
                return SubsystemHealth.degraded("llm", "No LLM providers registered");
            }
            return SubsystemHealth.up("llm");
        } catch (Exception e) {
            return SubsystemHealth.down("llm", e.getMessage());
        }
    }

    private SubsystemHealth probeCompiler() {
        try {
            Path cachePath = compiler.compileCachePath();
            if (!Files.exists(cachePath)) {
                Files.createDirectories(cachePath);
            }
            if (!Files.isDirectory(cachePath) || !Files.isWritable(cachePath)) {
                return SubsystemHealth.down("compiler", "Compile cache path is not a writable directory: " + cachePath);
            }
            return SubsystemHealth.up("compiler");
        } catch (Exception e) {
            return SubsystemHealth.down("compiler", e.getMessage());
        }
    }

    private SubsystemHealth probeAgent() {
        // InteractiveSessionFactory is assembled at build time; if we got here it's non-null
        return SubsystemHealth.up("agent");
    }

    private static List<String> parseSupportedBootstrapStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : content.replace("\r", "").split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
            if (line.endsWith(";")) {
                String statement = current.toString().trim();
                statement = statement.substring(0, statement.length() - 1).trim();
                validateSupportedBootstrapStatement(statement);
                statements.add(statement);
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            throw new BootstrapValidationException("services.sql contains an unterminated statement");
        }
        return List.copyOf(statements);
    }

    private static String stripComment(String line) {
        int commentStart = line.indexOf("--");
        if (commentStart >= 0) {
            return line.substring(0, commentStart);
        }
        return line;
    }

    private static void validateSupportedBootstrapStatement(String statement) {
        String normalized = statement.replaceAll("\\s+", " ").trim().toUpperCase();
        if (normalized.startsWith("CREATE TABLE IF NOT EXISTS ")) {
            return;
        }
        if (normalized.startsWith("CREATE SERVICE IF NOT EXISTS ")) {
            return;
        }
        throw new BootstrapValidationException(
                "Unsupported services.sql statement for first bootstrap contract: " + statement);
    }

    // --- Inner types ---

    public record ServiceApplicationBootstrapResult(Path sourcePath, int appliedStatements, List<String> statements) {

        public ServiceApplicationBootstrapResult {
            Objects.requireNonNull(sourcePath, "sourcePath must not be null");
            Objects.requireNonNull(statements, "statements must not be null");
        }
    }

    public static final class BootstrapValidationException extends IllegalArgumentException {

        public BootstrapValidationException(String message) {
            super(message);
        }
    }

    public record DatabaseCapability(String jdbcUrl) {

        public DatabaseCapability {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("jdbcUrl must not be null or blank");
            }
        }
    }

    public record LlmCapability(
            LlmProviderRegistry providerRegistry,
            Optional<RuntimeLlmConfigStore> runtimeConfigStore) {

        public LlmCapability {
            Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
            runtimeConfigStore = runtimeConfigStore == null ? Optional.empty() : runtimeConfigStore;
        }
    }

    public record CompilerCapability(
            SkillSourceCompiler sourceCompiler,
            ClassCacheManager classCacheManager,
            SkillHotLoader hotLoader,
            Path compileCachePath) {

        public CompilerCapability {
            Objects.requireNonNull(sourceCompiler, "sourceCompiler must not be null");
            Objects.requireNonNull(classCacheManager, "classCacheManager must not be null");
            Objects.requireNonNull(hotLoader, "hotLoader must not be null");
            Objects.requireNonNull(compileCachePath, "compileCachePath must not be null");
        }
    }

    public record InteractiveCapability(InteractiveSessionFactory sessionFactory) {

        public InteractiveCapability {
            Objects.requireNonNull(sessionFactory, "sessionFactory must not be null");
        }
    }

    public static final class SandlockCapability {

        private final SandlockRuntime runtime;
        private final Map<String, SandlockProfile> declaredProfiles;
        private final String selectedProfile;

        SandlockCapability(SandlockRuntime runtime, Map<String, SandlockProfile> declaredProfiles, String selectedProfile) {
            this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
            this.declaredProfiles = Map.copyOf(declaredProfiles == null ? Map.of() : new LinkedHashMap<>(declaredProfiles));
            this.selectedProfile = selectedProfile;
        }

        public SandlockExecutionResult execute(List<String> command) {
            return execute(null, command);
        }

        public SandlockExecutionResult execute(String requestedProfile, List<String> command) {
            List<String> normalizedCommand = normalizeCommand(command);
            SandlockProfile resolvedProfile = resolveProfile(requestedProfile);
            SandlockLaunchCheck check = runtime.check();
            if (!check.isAvailable()) {
                throw new SandlockExecutionException(check.failureCode(), check.message());
            }
            try {
                SandlockProcessOutput output = runtime.execute(resolvedProfile, normalizedCommand);
                return new SandlockExecutionResult(
                        resolvedProfile.name(),
                        normalizedCommand,
                        output.exitCode(),
                        output.stdout(),
                        output.stderr());
            } catch (SandlockExecutionException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SandlockExecutionException(
                        SandlockFailureCode.LAUNCH_FAILED,
                        "Sandlock-backed execution was interrupted before completion",
                        e);
            } catch (Exception e) {
                throw new SandlockExecutionException(
                        SandlockFailureCode.LAUNCH_FAILED,
                        "Failed to start Sandlock-backed execution: " + e.getMessage(),
                        e);
            }
        }

        private SandlockProfile resolveProfile(String requestedProfile) {
            if (requestedProfile != null) {
                String normalizedProfile = requestedProfile.trim();
                if (normalizedProfile.isEmpty()) {
                    throw new IllegalArgumentException("requestedProfile must not be blank");
                }
                SandlockProfile explicitProfile = declaredProfiles.get(normalizedProfile);
                if (explicitProfile == null) {
                    throw new SandlockExecutionException(
                            SandlockFailureCode.UNKNOWN_PROFILE,
                            "Unknown requested environment profile '" + normalizedProfile + "'");
                }
                explicitProfile.validateForExecution();
                return explicitProfile;
            }
            if (selectedProfile == null || selectedProfile.isBlank()) {
                throw new SandlockExecutionException(
                        SandlockFailureCode.NO_EFFECTIVE_PROFILE,
                        "No effective environment profile is available for Sandlock-backed execution");
            }
            SandlockProfile effectiveProfile = declaredProfiles.get(selectedProfile);
            if (effectiveProfile == null) {
                throw new SandlockExecutionException(
                        SandlockFailureCode.NO_EFFECTIVE_PROFILE,
                        "No effective environment profile is available for Sandlock-backed execution");
            }
            effectiveProfile.validateForExecution();
            return effectiveProfile;
        }

        private static List<String> normalizeCommand(List<String> command) {
            Objects.requireNonNull(command, "command must not be null");
            if (command.isEmpty()) {
                throw new IllegalArgumentException("command must not be empty");
            }
            List<String> normalized = new ArrayList<>(command.size());
            for (String part : command) {
                if (part == null || part.isBlank()) {
                    throw new IllegalArgumentException("command entries must not be null or blank");
                }
                normalized.add(part);
            }
            return List.copyOf(normalized);
        }
    }

    public record SandlockExecutionResult(
            String resolvedProfile,
            List<String> command,
            int exitCode,
            String stdout,
            String stderr) {

        public SandlockExecutionResult {
            if (resolvedProfile == null || resolvedProfile.isBlank()) {
                throw new IllegalArgumentException("resolvedProfile must not be null or blank");
            }
            Objects.requireNonNull(command, "command must not be null");
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
            command = List.copyOf(command);
        }
    }

    public static final class SandlockExecutionException extends IllegalStateException {

        private final SandlockFailureCode failureCode;

        public SandlockExecutionException(SandlockFailureCode failureCode, String message) {
            super(message);
            this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
        }

        public SandlockExecutionException(SandlockFailureCode failureCode, String message, Throwable cause) {
            super(message, cause);
            this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
        }

        public SandlockFailureCode failureCode() {
            return failureCode;
        }
    }

    public enum SandlockFailureCode {
        UNAVAILABLE,
        UNSUPPORTED_HOST,
        UNKNOWN_PROFILE,
        NO_EFFECTIVE_PROFILE,
        INVALID_PROFILE,
        LAUNCH_FAILED
    }

    interface SandlockRuntime {

        SandlockLaunchCheck check();

        SandlockProcessOutput execute(SandlockProfile resolvedProfile, List<String> command) throws Exception;
    }

    record SandlockLaunchCheck(SandlockFailureCode failureCode, String message) {

        static SandlockLaunchCheck ready() {
            return new SandlockLaunchCheck(null, null);
        }

        static SandlockLaunchCheck unavailable(String message) {
            return new SandlockLaunchCheck(SandlockFailureCode.UNAVAILABLE, message);
        }

        static SandlockLaunchCheck unsupportedHost(String message) {
            return new SandlockLaunchCheck(SandlockFailureCode.UNSUPPORTED_HOST, message);
        }

        boolean isAvailable() {
            return failureCode == null;
        }
    }

    record SandlockProcessOutput(int exitCode, String stdout, String stderr) {

        SandlockProcessOutput {
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
        }
    }

    public record SandlockProfile(
            String name,
            String isolatedHome,
            List<String> executableSearchPaths,
            Map<String, String> environmentOverrides,
            Map<String, String> cacheRoots,
            Map<String, Map<String, String>> toolchains) {

        private static final List<String> REQUIRED_CACHE_KEYS = List.of("maven", "npm", "go", "pip");

        public SandlockProfile {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be null or blank");
            }
            executableSearchPaths = List.copyOf(executableSearchPaths == null ? List.of() : executableSearchPaths);
            environmentOverrides = Map.copyOf(environmentOverrides == null ? Map.of() : new LinkedHashMap<>(environmentOverrides));
            cacheRoots = Map.copyOf(cacheRoots == null ? Map.of() : new LinkedHashMap<>(cacheRoots));
            if (toolchains == null || toolchains.isEmpty()) {
                toolchains = Map.of();
            } else {
                Map<String, Map<String, String>> copied = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, String>> entry : toolchains.entrySet()) {
                    copied.put(entry.getKey(), Map.copyOf(entry.getValue()));
                }
                toolchains = Map.copyOf(copied);
            }
        }

        static SandlockProfile fromFlatConfig(String profileName, Map<String, String> config) {
            Map<String, String> safeConfig = config == null ? Map.of() : config;
            String isolatedHome = trimToNull(safeConfig.get("runtime.home"));
            List<String> executableSearchPaths = splitSearchPath(safeConfig.get("runtime.path"));
            Map<String, String> environmentOverrides = extractPrefixed(safeConfig, "runtime.env.");
            Map<String, String> cacheRoots = extractCacheRoots(safeConfig);
            Map<String, Map<String, String>> toolchains = extractToolchains(safeConfig);
            return new SandlockProfile(profileName, isolatedHome, executableSearchPaths, environmentOverrides, cacheRoots,
                    toolchains);
        }

        void validateForExecution() {
            if (isolatedHome == null) {
                throw invalidProfile("Missing required isolation setting 'runtime.home' for profile '" + name + "'");
            }
            for (String cacheKey : REQUIRED_CACHE_KEYS) {
                String cachePath = cacheRoots.get(cacheKey);
                if (cachePath == null || cachePath.isBlank()) {
                    throw invalidProfile("Missing required isolation setting 'runtime.cache." + cacheKey
                            + "' for profile '" + name + "'");
                }
            }
        }

        Map<String, String> applyToEnvironment(Map<String, String> baseEnvironment) {
            Map<String, String> environment = new LinkedHashMap<>(baseEnvironment == null ? Map.of() : baseEnvironment);
            environment.put("HOME", isolatedHome);
            if (!executableSearchPaths.isEmpty()) {
                environment.put("PATH", String.join(java.io.File.pathSeparator, executableSearchPaths));
            }
            environment.put("MAVEN_USER_HOME", cacheRoots.get("maven"));
            environment.put("NPM_CONFIG_CACHE", cacheRoots.get("npm"));
            environment.put("GOMODCACHE", cacheRoots.get("go"));
            environment.put("GOCACHE", cacheRoots.get("go"));
            environment.put("PIP_CACHE_DIR", cacheRoots.get("pip"));
            environment.putAll(environmentOverrides);
            return environment;
        }

        private static Map<String, String> extractCacheRoots(Map<String, String> config) {
            Map<String, String> cacheRoots = new LinkedHashMap<>();
            for (String key : REQUIRED_CACHE_KEYS) {
                String value = trimToNull(config.get("runtime.cache." + key));
                if (value != null) {
                    cacheRoots.put(key, value);
                }
            }
            return cacheRoots;
        }

        private static Map<String, Map<String, String>> extractToolchains(Map<String, String> config) {
            Map<String, Map<String, String>> toolchains = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : config.entrySet()) {
                int separator = entry.getKey().indexOf('.');
                if (separator <= 0) {
                    continue;
                }
                String family = entry.getKey().substring(0, separator);
                if (!(family.equals("jdk") || family.equals("node") || family.equals("go") || family.equals("python"))) {
                    continue;
                }
                String field = entry.getKey().substring(separator + 1);
                if (field.isBlank()) {
                    continue;
                }
                toolchains.computeIfAbsent(family, ignored -> new LinkedHashMap<>())
                        .put(field, entry.getValue());
            }
            return toolchains;
        }

        private static Map<String, String> extractPrefixed(Map<String, String> config, String prefix) {
            Map<String, String> extracted = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : config.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    String key = entry.getKey().substring(prefix.length());
                    if (!key.isBlank()) {
                        extracted.put(key, entry.getValue());
                    }
                }
            }
            return extracted;
        }

        private static List<String> splitSearchPath(String rawPath) {
            String trimmed = trimToNull(rawPath);
            if (trimmed == null) {
                return List.of();
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String body = trimToNull(trimmed.substring(1, trimmed.length() - 1));
                if (body == null) {
                    return List.of();
                }
                List<String> parts = new ArrayList<>();
                for (String part : body.split(",")) {
                    String candidate = trimToNull(part);
                    if (candidate != null) {
                        parts.add(candidate);
                    }
                }
                return List.copyOf(parts);
            }
            List<String> parts = new ArrayList<>();
            for (String part : trimmed.split(Pattern.quote(java.io.File.pathSeparator))) {
                String candidate = trimToNull(part);
                if (candidate != null) {
                    parts.add(candidate);
                }
            }
            return List.copyOf(parts);
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static SandlockExecutionException invalidProfile(String message) {
            return new SandlockExecutionException(SandlockFailureCode.INVALID_PROFILE, message);
        }
    }

    static final class SystemSandlockRuntime implements SandlockRuntime {

        private static final String ENTRY_ENV = "SPEC_DRIVEN_SANDLOCK_ENTRY";
        private static final String BUNDLED_VERSION = "v0.6.0";
        private static final String BUNDLED_PLATFORM_DIR = "linux-x86_64";
        private static final String ENTRY_NAME = "sandlock";
        private final Map<String, String> environment;
        private final Path repositoryRoot;

        SystemSandlockRuntime() {
            this(System.getenv(), Path.of(System.getProperty("user.dir", ".")));
        }

        SystemSandlockRuntime(Map<String, String> environment, Path repositoryRoot) {
            this.environment = Map.copyOf(environment == null ? Map.of() : environment);
            this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot must not be null")
                    .toAbsolutePath()
                    .normalize();
        }

        @Override
        public SandlockLaunchCheck check() {
            if (!isSupportedHost()) {
                return SandlockLaunchCheck.unsupportedHost(
                        "The current host environment is unsupported for Sandlock-backed execution");
            }
            return resolveEntry().launchCheck();
        }

        @Override
        public SandlockProcessOutput execute(SandlockProfile resolvedProfile, List<String> command)
                throws IOException, InterruptedException {
            SandlockEntryResolution resolution = resolveEntry();
            if (!resolution.launchCheck().isAvailable()) {
                throw new SandlockExecutionException(
                        resolution.launchCheck().failureCode(),
                        resolution.launchCheck().message());
            }
            Path entryPath = Objects.requireNonNull(resolution.entryPath(), "entryPath must not be null when available");

            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(entryPath, resolvedProfile.name(), command));
            Map<String, String> processEnvironment = resolvedProfile.applyToEnvironment(processBuilder.environment());
            processBuilder.environment().clear();
            processBuilder.environment().putAll(processEnvironment);
            prepareBundledEnvironment(entryPath, processBuilder.environment());
            Process process = processBuilder.start();
            AtomicReference<String> stdout = new AtomicReference<>("");
            AtomicReference<String> stderr = new AtomicReference<>("");
            AtomicReference<RuntimeException> readFailure = new AtomicReference<>();
            Thread stdoutReader = Thread.ofVirtual().start(() -> stdout.set(readStream(process.getInputStream(), readFailure)));
            Thread stderrReader = Thread.ofVirtual().start(() -> stderr.set(readStream(process.getErrorStream(), readFailure)));

            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                process.destroyForcibly();
                joinQuietly(stdoutReader);
                joinQuietly(stderrReader);
                throw e;
            }
            stdoutReader.join();
            stderrReader.join();

            RuntimeException streamError = readFailure.get();
            if (streamError != null) {
                throw streamError;
            }
            return new SandlockProcessOutput(exitCode, stdout.get(), stderr.get());
        }

        private static boolean isSupportedHost() {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            return osName.contains("linux");
        }

        private SandlockEntryResolution resolveEntry() {
            String override = environment.get(ENTRY_ENV);
            if (override != null && !override.isBlank()) {
                try {
                    Path overridePath = Path.of(override).toAbsolutePath().normalize();
                    if (Files.isExecutable(overridePath)) {
                        return new SandlockEntryResolution(overridePath, SandlockLaunchCheck.ready());
                    }
                    return new SandlockEntryResolution(null, SandlockLaunchCheck.unavailable(
                            "Sandlock is unavailable because SPEC_DRIVEN_SANDLOCK_ENTRY is not executable: "
                                    + overridePath));
                } catch (InvalidPathException ignored) {
                    return new SandlockEntryResolution(null, SandlockLaunchCheck.unavailable(
                            "Sandlock is unavailable because SPEC_DRIVEN_SANDLOCK_ENTRY is invalid: " + override));
                }
            }
            Path bundledEntry = bundledEntryPath();
            if (Files.isExecutable(bundledEntry)) {
                return new SandlockEntryResolution(bundledEntry, SandlockLaunchCheck.ready());
            }
            return new SandlockEntryResolution(null, SandlockLaunchCheck.unavailable(
                    "Sandlock is unavailable because the repository-bundled entry was not found or is not executable: "
                            + bundledEntry));
        }

        Path bundledEntryPath() {
            return repositoryRoot
                    .resolve("depends")
                    .resolve("sandlock")
                    .resolve(BUNDLED_VERSION)
                    .resolve(BUNDLED_PLATFORM_DIR)
                    .resolve(ENTRY_NAME)
                    .toAbsolutePath()
                    .normalize();
        }

        private void prepareBundledEnvironment(Path entryPath, Map<String, String> processEnvironment) {
            Path bundledDir = bundledEntryPath().getParent();
            if (bundledDir == null) {
                return;
            }
            Path actualDir = entryPath.toAbsolutePath().normalize().getParent();
            if (actualDir == null || !actualDir.equals(bundledDir)) {
                return;
            }
            prependEnv(processEnvironment, "LD_LIBRARY_PATH", bundledDir.toString());
            prependEnv(processEnvironment, "PATH", bundledDir.toString());
        }

        private static void prependEnv(Map<String, String> environment, String key, String value) {
            String current = environment.get(key);
            if (current == null || current.isBlank()) {
                environment.put(key, value);
                return;
            }
            environment.put(key, value + java.io.File.pathSeparator + current);
        }

        private static List<String> buildCommand(Path entryPath, String resolvedProfile, List<String> command) {
            List<String> sandlockCommand = new ArrayList<>(command.size() + 5);
            sandlockCommand.add(entryPath.toString());
            sandlockCommand.add("run");
            sandlockCommand.add("--profile");
            sandlockCommand.add(resolvedProfile);
            sandlockCommand.add("--");
            sandlockCommand.addAll(command);
            return sandlockCommand;
        }

        private static String readStream(InputStream stream, AtomicReference<RuntimeException> readFailure) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                RuntimeException wrapped = new UncheckedIOException("Failed to capture Sandlock process output", e);
                readFailure.compareAndSet(null, wrapped);
                return "";
            }
        }

        private static void joinQuietly(Thread reader) {
            try {
                reader.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private record SandlockEntryResolution(Path entryPath, SandlockLaunchCheck launchCheck) {}
    }

    private record ConsumerRegistration(EventType type, Consumer<Event> listener) {}
}
