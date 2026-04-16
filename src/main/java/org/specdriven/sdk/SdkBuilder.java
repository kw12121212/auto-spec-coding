package org.specdriven.sdk;

import org.specdriven.agent.agent.*;
import org.specdriven.agent.config.Config;
import org.specdriven.agent.config.ConfigException;
import org.specdriven.agent.config.ConfigLoader;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.permission.PermissionProvider;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.MobileChannelConfig;
import org.specdriven.agent.question.MobileChannelProvider;
import org.specdriven.agent.question.MobileChannelRegistry;
import org.specdriven.agent.loop.InteractiveSessionFactory;
import org.specdriven.agent.llm.LealoneRuntimeLlmConfigStore;
import org.specdriven.agent.llm.RuntimeLlmConfigStore;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.builtin.BuiltinToolManager;
import org.specdriven.agent.tool.builtin.DefaultBuiltinToolManager;
import org.specdriven.agent.vault.VaultException;
import org.specdriven.agent.vault.VaultFactory;
import org.specdriven.skill.compiler.ClassCacheManager;
import org.specdriven.skill.compiler.LealoneClassCacheManager;
import org.specdriven.skill.compiler.LealoneSkillSourceCompiler;
import org.specdriven.skill.compiler.SkillSourceCompiler;
import org.specdriven.skill.hotload.LealoneSkillHotLoader;
import org.specdriven.skill.hotload.SkillHotLoader;

import java.nio.file.Path;
import java.util.*;

/**
 * Builder for constructing {@link SpecDriven} SDK instances.
 * Supports auto-assembly from YAML config or manual component injection.
 */
public class SdkBuilder {

    private static final String ENVIRONMENT_PROFILES_PREFIX = "environmentProfiles";
    private static final String SELECTED_ENVIRONMENT_PROFILE_KEY = ENVIRONMENT_PROFILES_PREFIX + ".selected";
    private static final String DECLARED_ENVIRONMENT_PROFILE_PREFIX = ENVIRONMENT_PROFILES_PREFIX + ".profiles.";

    private Path configPath;
    private LlmProviderRegistry providerRegistry;
    private final List<Tool> tools = new ArrayList<>();
    private String systemPrompt;
    private SdkConfig sdkConfig = SdkConfig.defaults();
    private final List<SdkEventListener> globalListeners = new ArrayList<>();
    private final Map<EventType, List<SdkEventListener>> typedGlobalListeners = new HashMap<>();
    private BuiltinToolManager builtinToolManager;
    private PermissionProvider permissionProvider;
    private DeliveryMode deliveryModeOverride;
    private final MobileChannelRegistry channelRegistry = new MobileChannelRegistry();
    private List<MobileChannelConfig> channelConfigs = Collections.emptyList();
    private PlatformConfig platformConfig;
    private String environmentProfile;
    private LealonePlatform.SandlockRuntime sandlockRuntime;

    SdkBuilder() {}

    public SdkBuilder config(Path configPath) {
        this.configPath = configPath;
        return this;
    }

    public SdkBuilder providerRegistry(LlmProviderRegistry registry) {
        this.providerRegistry = registry;
        return this;
    }

    public SdkBuilder registerTool(Tool tool) {
        this.tools.add(tool);
        return this;
    }

    public SdkBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public SdkBuilder sdkConfig(SdkConfig config) {
        this.sdkConfig = config;
        return this;
    }

    /**
     * Registers a global event listener that receives ALL events from every agent.
     */
    public SdkBuilder onEvent(SdkEventListener listener) {
        this.globalListeners.add(listener);
        return this;
    }

    /**
     * Registers a global event listener for a specific event type only.
     */
    public SdkBuilder onEvent(EventType type, SdkEventListener listener) {
        this.typedGlobalListeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
        return this;
    }

    /**
     * Sets the builtin tool manager. If not set, a default one will be created.
     */
    public SdkBuilder builtinToolManager(BuiltinToolManager manager) {
        this.builtinToolManager = manager;
        return this;
    }

    /**
     * Sets the permission provider for LLM config mutation guards.
     */
    public SdkBuilder permissionProvider(PermissionProvider provider) {
        this.permissionProvider = provider;
        return this;
    }

    /**
     * Sets a global delivery mode override applied to all agents.
     */
    public SdkBuilder deliveryModeOverride(DeliveryMode mode) {
        this.deliveryModeOverride = mode;
        return this;
    }

    /**
     * Registers a mobile channel provider by name.
     */
    public SdkBuilder registerChannelProvider(String name, MobileChannelProvider provider) {
        this.channelRegistry.registerProvider(name, provider);
        return this;
    }

    /**
     * Sets mobile channel configurations for assembly at build time.
     */
    public SdkBuilder channelConfigs(List<MobileChannelConfig> configs) {
        this.channelConfigs = configs;
        return this;
    }

    /**
     * Sets the platform configuration (JDBC URL, compile cache path).
     * When not set, {@link PlatformConfig#defaults()} is used, or values are derived
     * from {@code platform.*} keys in the YAML config file if one is loaded.
     */
    public SdkBuilder platformConfig(PlatformConfig config) {
        this.platformConfig = config;
        return this;
    }

    /**
     * Selects an explicit environment profile name from the project YAML profile set.
     */
    public SdkBuilder environmentProfile(String profileName) {
        this.environmentProfile = profileName;
        return this;
    }

    SdkBuilder sandlockRuntime(LealonePlatform.SandlockRuntime sandlockRuntime) {
        this.sandlockRuntime = sandlockRuntime;
        return this;
    }

    public SpecDriven build() {
        try {
            AssembledComponents assembled = assembleComponents();

            return new SpecDriven(
                    assembled.platform(),
                    assembled.sdkProviderRegistry(),
                    List.copyOf(tools),
                    assembled.effectiveSystemPrompt(),
                    sdkConfig,
                    assembled.configMap(),
                    assembled.eventBus(),
                    deliveryModeOverride,
                    channelRegistry,
                    channelConfigs
            );
        } catch (ConfigException e) {
            throw new SdkConfigException("Failed to load config: " + configPath, e);
        } catch (VaultException e) {
            throw new SdkVaultException("Vault resolution failed: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof SdkException) throw (SdkException) e;
            throw new SdkException("Failed to build SDK: " + e.getMessage(), e);
        }
    }

    public LealonePlatform buildPlatform() {
        try {
            return assembleComponents().platform();
        } catch (ConfigException e) {
            throw new SdkConfigException("Failed to load config: " + configPath, e);
        } catch (VaultException e) {
            throw new SdkVaultException("Vault resolution failed: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof SdkException) throw (SdkException) e;
            throw new SdkException("Failed to build platform: " + e.getMessage(), e);
        }
    }

    private AssembledComponents assembleComponents() {
        PlatformConfig effectivePlatform = deriveEffectivePlatformConfig();

        Map<String, String> configMap = Collections.emptyMap();
        LlmProviderRegistry registry = this.providerRegistry;
        LlmProviderRegistry sdkProviderRegistry = this.providerRegistry;
        SimpleEventBus eventBus = new SimpleEventBus();
        Optional<RuntimeLlmConfigStore> runtimeConfigStore = tryCreateRuntimeConfigStore(effectivePlatform.jdbcUrl());
        Config config = null;

        if (configPath != null) {
            config = ConfigLoader.load(configPath, true);
            configMap = config.asMap();
            configMap = applyEnvironmentProfileSelection(config, configMap);

            Map<String, String> resolvedMap = tryResolveVault(configMap);
            if (resolvedMap != null) {
                configMap = resolvedMap;
            }
        }

        if (config != null && registry == null) {
            Map<String, LlmProviderFactory> factories = new HashMap<>();
            factories.put("openai", new OpenAiProviderFactory());
            factories.put("claude", new ClaudeProviderFactory());
            registry = DefaultLlmProviderRegistry.fromConfig(config, factories, runtimeConfigStore.orElse(null), eventBus,
                    permissionProvider);
            sdkProviderRegistry = registry;
        }

        if (registry == null) {
            registry = new DefaultLlmProviderRegistry(runtimeConfigStore.orElse(null), eventBus, permissionProvider);
        }

        wireGlobalListeners(eventBus);

        String effectiveSystemPrompt = systemPrompt != null ? systemPrompt : sdkConfig.systemPrompt();
        SkillSourceCompiler sourceCompiler = new LealoneSkillSourceCompiler();
        ClassCacheManager classCacheManager = new LealoneClassCacheManager(effectivePlatform.compileCachePath());
        SkillHotLoader hotLoader = new LealoneSkillHotLoader(sourceCompiler, classCacheManager, false);
        String jdbcUrl = effectivePlatform.jdbcUrl();
        InteractiveSessionFactory sessionFactory = sessionId -> new org.specdriven.agent.interactive.LealoneAgentAdapter(
                jdbcUrl);

        LealonePlatform platform = new LealonePlatform(
                new LealonePlatform.DatabaseCapability(jdbcUrl),
                new LealonePlatform.LlmCapability(registry, runtimeConfigStore),
                new LealonePlatform.CompilerCapability(sourceCompiler, classCacheManager, hotLoader,
                        effectivePlatform.compileCachePath()),
                new LealonePlatform.InteractiveCapability(sessionFactory),
                new LealonePlatform.SandlockCapability(
                        sandlockRuntime != null ? sandlockRuntime : new LealonePlatform.SystemSandlockRuntime(),
                        extractEnvironmentProfiles(configMap),
                        configMap.get(SELECTED_ENVIRONMENT_PROFILE_KEY)),
                eventBus);

        return new AssembledComponents(platform, sdkProviderRegistry, configMap, eventBus,
                effectiveSystemPrompt);
    }

    private Map<String, String> applyEnvironmentProfileSelection(Config config, Map<String, String> configMap) {
        String defaultProfile = config.getString(ENVIRONMENT_PROFILES_PREFIX + ".default", null);
        if (defaultProfile == null) {
            return configMap;
        }
        String selectedProfile = environmentProfile != null ? environmentProfile : defaultProfile;
        String selectedPrefix = ENVIRONMENT_PROFILES_PREFIX + ".profiles." + selectedProfile;
        Config selectedConfig;
        try {
            selectedConfig = config.getSection(selectedPrefix);
        } catch (ConfigException e) {
            String reason = environmentProfile != null
                    ? "Unknown requested environment profile '" + selectedProfile + "'"
                    : "Default environment profile '" + selectedProfile + "' does not match any declared profile";
            throw new ConfigException(reason + " in " + config, e);
        }
        Map<String, String> selectedValues = selectedConfig.asMap();
        Map<String, String> enriched = new LinkedHashMap<>(configMap);
        enriched.put(ENVIRONMENT_PROFILES_PREFIX + ".selected", selectedProfile);
        for (Map.Entry<String, String> entry : selectedValues.entrySet()) {
            enriched.put(ENVIRONMENT_PROFILES_PREFIX + ".selected." + entry.getKey(), entry.getValue());
        }
        return Map.copyOf(enriched);
    }

    private PlatformConfig deriveEffectivePlatformConfig() {
        if (platformConfig != null) return platformConfig;
        if (configPath != null) {
            try {
                Config config = ConfigLoader.load(configPath, false);
                PlatformConfig defaults = PlatformConfig.defaults();
                String jdbcUrl = config.getString("platform.jdbcUrl", defaults.jdbcUrl());
                String cachePathStr = config.getString("platform.compileCachePath",
                        defaults.compileCachePath().toString());
                return new PlatformConfig(jdbcUrl, Path.of(cachePathStr));
            } catch (Exception ignored) {
                // Config file may not exist yet or may not contain platform keys — fall through
            }
        }
        return PlatformConfig.defaults();
    }

    private Map<String, LealonePlatform.SandlockProfile> extractEnvironmentProfiles(Map<String, String> configMap) {
        Map<String, Map<String, String>> rawProfiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(DECLARED_ENVIRONMENT_PROFILE_PREFIX)) {
                continue;
            }
            String remainder = key.substring(DECLARED_ENVIRONMENT_PROFILE_PREFIX.length());
            int separator = remainder.indexOf('.');
            if (separator <= 0) {
                continue;
            }
            String profileName = remainder.substring(0, separator);
            String profileKey = remainder.substring(separator + 1);
            rawProfiles.computeIfAbsent(profileName, ignored -> new LinkedHashMap<>())
                    .put(profileKey, entry.getValue());
        }

        Map<String, LealonePlatform.SandlockProfile> profiles = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : rawProfiles.entrySet()) {
            profiles.put(entry.getKey(), LealonePlatform.SandlockProfile.fromFlatConfig(entry.getKey(), entry.getValue()));
        }
        return Map.copyOf(profiles);
    }

    private void wireGlobalListeners(EventBus eventBus) {
        for (SdkEventListener listener : globalListeners) {
            for (EventType type : EventType.values()) {
                eventBus.subscribe(type, listener);
            }
        }
        for (Map.Entry<EventType, List<SdkEventListener>> entry : typedGlobalListeners.entrySet()) {
            for (SdkEventListener listener : entry.getValue()) {
                eventBus.subscribe(entry.getKey(), listener);
            }
        }
    }

    private Optional<RuntimeLlmConfigStore> tryCreateRuntimeConfigStore(String jdbcUrl) {
        try {
            return Optional.of(new LealoneRuntimeLlmConfigStore(jdbcUrl));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private record AssembledComponents(
            LealonePlatform platform,
            LlmProviderRegistry sdkProviderRegistry,
            Map<String, String> configMap,
            SimpleEventBus eventBus,
            String effectiveSystemPrompt) {
    }

    /**
     * Attempts vault resolution. Returns null if vault is not available.
     */
    private Map<String, String> tryResolveVault(Map<String, String> configMap) {
        boolean hasVaultRefs = configMap.values().stream()
                .anyMatch(v -> v != null && v.startsWith("vault:"));
        if (!hasVaultRefs) {
            return null;
        }
        String masterKey = System.getenv("SPEC_DRIVEN_MASTER_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            return null;
        }
        try {
            var vault = VaultFactory.create(new SimpleEventBus());
            return org.specdriven.agent.vault.VaultResolver.resolve(configMap, vault);
        } catch (Exception e) {
            return null;
        }
    }
}
