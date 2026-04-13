package org.specdriven.agent.agent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.specdriven.agent.config.Config;
import org.specdriven.agent.llm.RuntimeLlmConfigStore;

/**
 * Thread-safe implementation of {@link LlmProviderRegistry}.
 * Manages named provider instances with default fallback and skill-based routing.
 */
public class DefaultLlmProviderRegistry implements LlmProviderRegistry {

    private final ConcurrentHashMap<String, LlmProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkillRoute> skillRouting = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LlmConfigSnapshot> sessionSnapshots = new ConcurrentHashMap<>();
    private final AtomicReference<LlmConfigSnapshot> defaultSnapshotOverride = new AtomicReference<>();
    private final RuntimeLlmConfigStore runtimeConfigStore;
    private volatile String defaultProviderName;

    public DefaultLlmProviderRegistry() {
        this(null);
    }

    public DefaultLlmProviderRegistry(RuntimeLlmConfigStore runtimeConfigStore) {
        this.runtimeConfigStore = runtimeConfigStore;
        if (runtimeConfigStore != null) {
            defaultSnapshotOverride.set(runtimeConfigStore.loadDefaultSnapshot().orElse(null));
        }
    }

    @Override
    public void register(String name, LlmProvider provider) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("provider name must not be null or blank");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        LlmProvider existing = providers.putIfAbsent(name, provider);
        if (existing != null) {
            throw new IllegalArgumentException("provider '" + name + "' is already registered");
        }
    }

    @Override
    public LlmProvider provider(String name) {
        LlmProvider p = providers.get(name);
        if (p == null) {
            throw new IllegalArgumentException("no provider registered under name '" + name + "'");
        }
        return p;
    }

    @Override
    public LlmProvider defaultProvider() {
        return provider(resolveDefaultProviderName());
    }

    @Override
    public LlmConfigSnapshot defaultSnapshot() {
        LlmConfigSnapshot override = defaultSnapshotOverride.get();
        if (override != null) {
            if (providers.isEmpty() || providers.containsKey(override.providerName())) {
                return override;
            }
            defaultSnapshotOverride.compareAndSet(override, null);
        }
        String providerName = resolveDefaultProviderName();
        return LlmConfigSnapshot.of(providerName, provider(providerName).config());
    }

    @Override
    public LlmConfigSnapshot snapshot(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return defaultSnapshot();
        }
        LlmConfigSnapshot snapshot = sessionSnapshots.get(sessionId);
        return snapshot != null ? snapshot : defaultSnapshot();
    }

    @Override
    public Set<String> providerNames() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    @Override
    public void remove(String name) {
        LlmProvider removed = providers.remove(name);
        if (removed == null) {
            throw new IllegalArgumentException("no provider registered under name '" + name + "'");
        }
        // auto-close the removed provider
        try {
            removed.close();
        } catch (Exception ignored) {
            // close best-effort
        }
        // clear default if it was the removed provider
        if (name.equals(defaultProviderName)) {
            defaultProviderName = null;
        }
        if (name.equals(defaultSnapshotOverride.get() != null ? defaultSnapshotOverride.get().providerName() : null)) {
            defaultSnapshotOverride.set(null);
        }
        sessionSnapshots.entrySet().removeIf(entry -> name.equals(entry.getValue().providerName()));
    }

    @Override
    public void setDefault(String name) {
        if (!providers.containsKey(name)) {
            throw new IllegalArgumentException("no provider registered under name '" + name + "'");
        }
        defaultProviderName = name;
    }

    @Override
    public void replaceDefaultSnapshot(LlmConfigSnapshot snapshot) {
        LlmConfigSnapshot validated = validateSnapshot(snapshot);
        if (runtimeConfigStore != null) {
            runtimeConfigStore.persistDefaultSnapshot(validated);
        }
        defaultSnapshotOverride.set(validated);
    }

    @Override
    public void replaceSessionSnapshot(String sessionId, LlmConfigSnapshot snapshot) {
        requireSessionId(sessionId);
        sessionSnapshots.put(sessionId, validateSnapshot(snapshot));
    }

    @Override
    public void clearSessionSnapshot(String sessionId) {
        requireSessionId(sessionId);
        sessionSnapshots.remove(sessionId);
    }

    @Override
    public SkillRoute route(String skillName) {
        return skillRouting.get(skillName);
    }

    @Override
    public void addSkillRoute(String skillName, SkillRoute route) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be null or blank");
        }
        if (route == null) {
            throw new IllegalArgumentException("route must not be null");
        }
        skillRouting.put(skillName, route);
    }

    @Override
    public LlmClient createClientForSession(String sessionId) {
        Supplier<LlmConfigSnapshot> supplier = () -> snapshot(sessionId);
        return new SnapshotAwareLlmClient(supplier);
    }

    @Override
    public void close() {
        // close all providers, collecting errors
        Exception firstError = null;
        for (Map.Entry<String, LlmProvider> entry : providers.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                if (firstError == null) firstError = e;
            }
        }
        providers.clear();
        skillRouting.clear();
        sessionSnapshots.clear();
        defaultSnapshotOverride.set(null);
        defaultProviderName = null;
        if (runtimeConfigStore != null) {
            try {
                runtimeConfigStore.close();
            } catch (Exception e) {
                if (firstError == null) firstError = e;
            }
        }
        if (firstError != null) {
            throw new RuntimeException("error closing providers", firstError);
        }
    }

    private LlmConfigSnapshot validateSnapshot(LlmConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        provider(snapshot.providerName());
        return snapshot;
    }

    private void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be null or blank");
        }
    }

    private String resolveDefaultProviderName() {
        String name = defaultProviderName;
        if (name != null) {
            LlmProvider provider = providers.get(name);
            if (provider != null) {
                return name;
            }
        }
        Iterator<String> it = providers.keySet().iterator();
        if (it.hasNext()) {
            return it.next();
        }
        throw new IllegalStateException("no providers registered");
    }

    private final class SnapshotAwareLlmClient implements LlmClient {
        private final Supplier<LlmConfigSnapshot> snapshotSupplier;

        private SnapshotAwareLlmClient(Supplier<LlmConfigSnapshot> snapshotSupplier) {
            this.snapshotSupplier = snapshotSupplier;
        }

        @Override
        public LlmResponse chat(List<Message> messages) {
            return chat(LlmRequest.of(messages));
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            LlmConfigSnapshot snapshot = snapshotSupplier.get();
            return provider(snapshot.providerName()).createClient(snapshot).chat(request);
        }

        @Override
        public void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
            LlmConfigSnapshot snapshot = snapshotSupplier.get();
            provider(snapshot.providerName()).createClient(snapshot).chatStreaming(request, callback);
        }
    }

    /**
     * Creates a registry from a {@link Config} instance.
     * <p>
     * Expected config structure:
     * <pre>
     * llm:
     *   providers:
     *     openai-main:
     *       baseUrl: "..."
     *       apiKey: "..."
     *       model: "gpt-4"
     *   default: "openai-main"
     *   skill-routing:
     *     code-review:
     *       provider: "claude"
     *       model: "claude-opus-4-6-20250514"
     * </pre>
     *
     * @param config    the root config
     * @param factories map of factory type names to factory instances
     *                  (e.g. "openai" → OpenAiProviderFactory)
     * @return a populated DefaultLlmProviderRegistry
     */
    public static DefaultLlmProviderRegistry fromConfig(
            Config config,
            Map<String, LlmProviderFactory> factories) {
        return fromConfig(config, factories, null);
    }

    public static DefaultLlmProviderRegistry fromConfig(
            Config config,
            Map<String, LlmProviderFactory> factories,
            RuntimeLlmConfigStore runtimeConfigStore) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(factories, "factories must not be null");

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(runtimeConfigStore);

        // navigate to llm section
        Config llmSection = config.getSection("llm");

        // parse providers
        String firstProviderName = null;

        for (String providerName : getProviderNames(llmSection)) {
            if (firstProviderName == null) firstProviderName = providerName;

            Config providerSection = llmSection.getSection("providers." + providerName);
            Map<String, String> providerMap = providerSection.asMap();

            // determine factory type from config or infer from baseUrl
            String factoryType = providerMap.getOrDefault("type", inferFactoryType(providerMap));
            LlmProviderFactory factory = factories.get(factoryType);
            if (factory == null) {
                throw new IllegalArgumentException(
                        "no factory registered for type '" + factoryType + "' (provider: " + providerName + ")");
            }

            LlmConfig llmConfig = LlmConfig.fromMap(providerMap);
            LlmProvider provider = factory.create(llmConfig);
            registry.register(providerName, provider);
        }

        // set default
        String defaultName = llmSection.getString("default", firstProviderName);
        if (defaultName != null && registry.providers.containsKey(defaultName)) {
            registry.setDefault(defaultName);
        }

        // parse skill-routing
        try {
            Config routingSection = llmSection.getSection("skill-routing");
            Map<String, String> routingMap = routingSection.asMap();
            for (Map.Entry<String, String> entry : routingMap.entrySet()) {
                // keys like "code-review.provider", "code-review.model"
                String key = entry.getKey();
                int dot = key.indexOf('.');
                if (dot < 0) continue;
                String skillName = key.substring(0, dot);
                // check if we already have this skill — addSkillRoute when we have provider
                if (key.endsWith(".provider")) {
                    String provider = entry.getValue();
                    String model = routingMap.get(skillName + ".model");
                    registry.addSkillRoute(skillName, new SkillRoute(provider, model));
                }
            }
        } catch (Exception ignored) {
            // skill-routing is optional
        }

        return registry;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getProviderNames(Config llmSection) {
        // Access the raw providers map to get top-level keys
        try {
            Config providersSection = llmSection.getSection("providers");
            // use asMap and extract unique top-level prefixes
            Map<String, String> flat = providersSection.asMap();
            Set<String> names = new LinkedHashSet<>();
            for (String key : flat.keySet()) {
                int dot = key.indexOf('.');
                names.add(dot < 0 ? key : key.substring(0, dot));
            }
            return names;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private static String inferFactoryType(Map<String, String> providerMap) {
        String baseUrl = providerMap.getOrDefault("baseUrl", "").toLowerCase();
        if (baseUrl.contains("anthropic")) return "claude";
        return "openai"; // default to openai-compatible
    }
}
