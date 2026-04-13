package org.specdriven.agent.agent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.specdriven.agent.config.Config;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.llm.RuntimeLlmConfigStore;

/**
 * Thread-safe implementation of {@link LlmProviderRegistry}.
 * Manages named provider instances with default fallback and skill-based routing.
 */
public class DefaultLlmProviderRegistry implements LlmProviderRegistry {

    private static final System.Logger LOG = System.getLogger(DefaultLlmProviderRegistry.class.getName());

    private static final Set<String> SUPPORTED_SET_LLM_KEYS = Set.of("provider", "model", "base_url", "timeout", "max_retries");
    private static final String DEFAULT_SCOPE = "default";
    private static final String SESSION_SCOPE = "session";
    private static final String EVENT_SOURCE = "llm-runtime-config";

    private final ConcurrentHashMap<String, LlmProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkillRoute> skillRouting = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LlmConfigSnapshot> sessionSnapshots = new ConcurrentHashMap<>();
    private final AtomicReference<LlmConfigSnapshot> defaultSnapshotOverride = new AtomicReference<>();
    private final RuntimeLlmConfigStore runtimeConfigStore;
    private final EventBus eventBus;
    private final SetLlmStatementParser setLlmStatementParser = new SetLlmStatementParser();
    private volatile String defaultProviderName;

    public DefaultLlmProviderRegistry() {
        this(null, null);
    }

    public DefaultLlmProviderRegistry(RuntimeLlmConfigStore runtimeConfigStore) {
        this(runtimeConfigStore, null);
    }

    public DefaultLlmProviderRegistry(RuntimeLlmConfigStore runtimeConfigStore, EventBus eventBus) {
        this.runtimeConfigStore = runtimeConfigStore;
        this.eventBus = eventBus;
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
        LlmConfigSnapshot previous = defaultSnapshot();
        LlmConfigSnapshot validated = validateSnapshot(snapshot);
        if (runtimeConfigStore != null) {
            runtimeConfigStore.persistDefaultSnapshot(validated);
        }
        defaultSnapshotOverride.set(validated);
        publishConfigChanged(DEFAULT_SCOPE, null, previous, validated);
    }

    @Override
    public void replaceSessionSnapshot(String sessionId, LlmConfigSnapshot snapshot) {
        requireSessionId(sessionId);
        LlmConfigSnapshot previous = snapshot(sessionId);
        LlmConfigSnapshot validated = validateSnapshot(snapshot);
        sessionSnapshots.put(sessionId, validated);
        publishConfigChanged(SESSION_SCOPE, sessionId, previous, validated);
    }

    @Override
    public LlmConfigSnapshot applySetLlmStatement(String sessionId, String sql) {
        requireSessionId(sessionId);
        Map<String, String> assignments = setLlmStatementParser.parseAssignments(sql);
        LlmConfigSnapshot current = snapshot(sessionId);
        LlmConfigSnapshot replacement = buildReplacementSnapshot(current, assignments);
        sessionSnapshots.put(sessionId, replacement);
        publishConfigChanged(SESSION_SCOPE, sessionId, current, replacement);
        return replacement;
    }

    @Override
    public void clearSessionSnapshot(String sessionId) {
        requireSessionId(sessionId);
        LlmConfigSnapshot previous = sessionSnapshots.remove(sessionId);
        if (previous != null) {
            publishConfigChanged(SESSION_SCOPE, sessionId, previous, snapshot(sessionId));
        }
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

    private LlmConfigSnapshot buildReplacementSnapshot(LlmConfigSnapshot current, Map<String, String> assignments) {
        String providerName = current.providerName();
        String baseUrl = current.baseUrl();
        String model = current.model();
        int timeout = current.timeout();
        int maxRetries = current.maxRetries();

        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!SUPPORTED_SET_LLM_KEYS.contains(key)) {
                throw new SetLlmSqlException("Unsupported SET LLM parameter '" + key + "'");
            }
            switch (key) {
                case "provider" -> providerName = requireRegisteredProvider(value);
                case "model" -> model = requireNonBlankValue(key, value);
                case "base_url" -> baseUrl = requireNonBlankValue(key, value);
                case "timeout" -> timeout = parsePositiveInt(key, value);
                case "max_retries" -> maxRetries = parseNonNegativeInt(key, value);
                default -> throw new SetLlmSqlException("Unsupported SET LLM parameter '" + key + "'");
            }
        }

        return validateSnapshot(new LlmConfigSnapshot(providerName, baseUrl, model, timeout, maxRetries));
    }

    private String requireRegisteredProvider(String providerName) {
        String resolved = requireNonBlankValue("provider", providerName);
        try {
            provider(resolved);
            return resolved;
        } catch (IllegalArgumentException e) {
            throw new SetLlmSqlException("Unsupported SET LLM provider '" + resolved + "'", e);
        }
    }

    private String requireNonBlankValue(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new SetLlmSqlException("SET LLM parameter '" + key + "' must not be blank");
        }
        return value;
    }

    private int parsePositiveInt(String key, String value) {
        int parsed = parseInteger(key, value);
        if (parsed <= 0) {
            throw new SetLlmSqlException("SET LLM parameter '" + key + "' must be positive");
        }
        return parsed;
    }

    private int parseNonNegativeInt(String key, String value) {
        int parsed = parseInteger(key, value);
        if (parsed < 0) {
            throw new SetLlmSqlException("SET LLM parameter '" + key + "' must not be negative");
        }
        return parsed;
    }

    private int parseInteger(String key, String value) {
        String normalized = requireNonBlankValue(key, value);
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new SetLlmSqlException("SET LLM parameter '" + key + "' must be an integer", e);
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
        return fromConfig(config, factories, null, null);
    }

    public static DefaultLlmProviderRegistry fromConfig(
            Config config,
            Map<String, LlmProviderFactory> factories,
            EventBus eventBus) {
        return fromConfig(config, factories, null, eventBus);
    }

    public static DefaultLlmProviderRegistry fromConfig(
            Config config,
            Map<String, LlmProviderFactory> factories,
            RuntimeLlmConfigStore runtimeConfigStore) {
        return fromConfig(config, factories, runtimeConfigStore, null);
    }

    public static DefaultLlmProviderRegistry fromConfig(
            Config config,
            Map<String, LlmProviderFactory> factories,
            RuntimeLlmConfigStore runtimeConfigStore,
            EventBus eventBus) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(factories, "factories must not be null");

        DefaultLlmProviderRegistry registry = new DefaultLlmProviderRegistry(runtimeConfigStore, eventBus);

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

    private void publishConfigChanged(String scope, String sessionId, LlmConfigSnapshot before, LlmConfigSnapshot after) {
        if (eventBus == null) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", scope);
        if (SESSION_SCOPE.equals(scope)) {
            metadata.put("sessionId", sessionId);
        }
        metadata.put("provider", after.providerName());
        metadata.put("changedKeys", String.join(",", changedKeys(before, after)));
        try {
            eventBus.publish(new Event(EventType.LLM_CONFIG_CHANGED, System.currentTimeMillis(), EVENT_SOURCE, metadata));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to publish runtime LLM config change event", e);
        }
    }

    private List<String> changedKeys(LlmConfigSnapshot before, LlmConfigSnapshot after) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(before.providerName(), after.providerName())) {
            changed.add("provider");
        }
        if (!Objects.equals(before.baseUrl(), after.baseUrl())) {
            changed.add("baseUrl");
        }
        if (!Objects.equals(before.model(), after.model())) {
            changed.add("model");
        }
        if (before.timeout() != after.timeout()) {
            changed.add("timeout");
        }
        if (before.maxRetries() != after.maxRetries()) {
            changed.add("maxRetries");
        }
        return changed;
    }
}
