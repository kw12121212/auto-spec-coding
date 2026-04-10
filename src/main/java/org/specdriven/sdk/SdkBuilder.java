package org.specdriven.sdk;

import org.specdriven.agent.agent.*;
import org.specdriven.agent.config.Config;
import org.specdriven.agent.config.ConfigException;
import org.specdriven.agent.config.ConfigLoader;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.MobileChannelConfig;
import org.specdriven.agent.question.MobileChannelProvider;
import org.specdriven.agent.question.MobileChannelRegistry;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.builtin.BuiltinToolManager;
import org.specdriven.agent.tool.builtin.DefaultBuiltinToolManager;
import org.specdriven.agent.vault.VaultException;
import org.specdriven.agent.vault.VaultFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * Builder for constructing {@link SpecDriven} SDK instances.
 * Supports auto-assembly from YAML config or manual component injection.
 */
public class SdkBuilder {

    private Path configPath;
    private LlmProviderRegistry providerRegistry;
    private final List<Tool> tools = new ArrayList<>();
    private String systemPrompt;
    private SdkConfig sdkConfig = SdkConfig.defaults();
    private final List<SdkEventListener> globalListeners = new ArrayList<>();
    private final Map<EventType, List<SdkEventListener>> typedGlobalListeners = new HashMap<>();
    private BuiltinToolManager builtinToolManager;
    private DeliveryMode deliveryModeOverride;
    private final MobileChannelRegistry channelRegistry = new MobileChannelRegistry();
    private List<MobileChannelConfig> channelConfigs = Collections.emptyList();

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

    public SpecDriven build() {
        try {
            Map<String, String> configMap = Collections.emptyMap();
            LlmProviderRegistry registry = this.providerRegistry;

            // Auto-assembly from config file
            if (configPath != null && registry == null) {
                Config config = ConfigLoader.load(configPath, true);
                configMap = config.asMap();

                // Try vault-aware loading if vault references exist
                Map<String, String> resolvedMap = tryResolveVault(configMap);
                if (resolvedMap != null) {
                    configMap = resolvedMap;
                }

                // Auto-assemble provider registry
                Map<String, LlmProviderFactory> factories = new HashMap<>();
                factories.put("openai", new OpenAiProviderFactory());
                factories.put("claude", new ClaudeProviderFactory());
                registry = DefaultLlmProviderRegistry.fromConfig(config, factories);
            }

            // Merge system prompt: explicit > sdkConfig > config
            String effectiveSystemPrompt = systemPrompt != null ? systemPrompt : sdkConfig.systemPrompt();

            // Create shared event bus and wire global listeners
            SimpleEventBus eventBus = new SimpleEventBus();
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

            return new SpecDriven(
                    registry,
                    List.copyOf(tools),
                    effectiveSystemPrompt,
                    sdkConfig,
                    configMap,
                    eventBus,
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
