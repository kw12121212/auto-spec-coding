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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Public platform-level entry point for assembled Lealone-centered capabilities.
 */
public final class LealonePlatform implements AutoCloseable {

    static final String DEFAULT_JDBC_URL = "jdbc:lealone:embed:agent_db";

    private final DatabaseCapability database;
    private final LlmCapability llm;
    private final CompilerCapability compiler;
    private final InteractiveCapability interactive;
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
            EventBus eventBus) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.llm = Objects.requireNonNull(llm, "llm must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.interactive = Objects.requireNonNull(interactive, "interactive must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
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

    private record ConsumerRegistration(EventType type, Consumer<Event> listener) {}
}
