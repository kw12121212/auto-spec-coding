package org.specdriven.sdk;

import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.llm.RuntimeLlmConfigStore;
import org.specdriven.agent.loop.InteractiveSessionFactory;
import org.specdriven.skill.compiler.ClassCacheManager;
import org.specdriven.skill.compiler.SkillSourceCompiler;
import org.specdriven.skill.hotload.SkillHotLoader;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Public platform-level entry point for assembled Lealone-centered capabilities.
 */
public final class LealonePlatform implements AutoCloseable {

    static final String DEFAULT_JDBC_URL = "jdbc:lealone:embed:agent_db";

    private final DatabaseCapability database;
    private final LlmCapability llm;
    private final CompilerCapability compiler;
    private final InteractiveCapability interactive;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    LealonePlatform(
            DatabaseCapability database,
            LlmCapability llm,
            CompilerCapability compiler,
            InteractiveCapability interactive) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.llm = Objects.requireNonNull(llm, "llm must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.interactive = Objects.requireNonNull(interactive, "interactive must not be null");
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
     * Records the platform as running. Safe to call multiple times (idempotent).
     */
    public void start() {
        started.compareAndSet(false, true);
    }

    /**
     * Tears down all capability domains in reverse dependency order with per-subsystem
     * exception suppression. Safe to call multiple times (idempotent).
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
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
            SkillHotLoader hotLoader) {

        public CompilerCapability {
            Objects.requireNonNull(sourceCompiler, "sourceCompiler must not be null");
            Objects.requireNonNull(classCacheManager, "classCacheManager must not be null");
            Objects.requireNonNull(hotLoader, "hotLoader must not be null");
        }
    }

    public record InteractiveCapability(InteractiveSessionFactory sessionFactory) {

        public InteractiveCapability {
            Objects.requireNonNull(sessionFactory, "sessionFactory must not be null");
        }
    }
}
