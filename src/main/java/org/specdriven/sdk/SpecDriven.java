package org.specdriven.sdk;

import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.tool.Tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for the SpecDriven SDK.
 * Create instances via {@link #builder()}.
 */
public class SpecDriven implements AutoCloseable {

    private final LlmProviderRegistry providerRegistry;
    private final List<Tool> tools;
    private final String systemPrompt;
    private final SdkConfig sdkConfig;
    private final Map<String, String> configMap;
    private final EventBus eventBus;

    SpecDriven(LlmProviderRegistry providerRegistry,
               List<Tool> tools,
               String systemPrompt,
               SdkConfig sdkConfig,
               Map<String, String> configMap,
               EventBus eventBus) {
        this.providerRegistry = providerRegistry;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.sdkConfig = sdkConfig;
        this.configMap = configMap;
        this.eventBus = eventBus;
    }

    /**
     * Returns a new builder for constructing SDK instances.
     */
    public static SdkBuilder builder() {
        return new SdkBuilder();
    }

    /**
     * Creates a new agent handle configured with this SDK's tools and providers.
     */
    public SdkAgent createAgent() {
        Map<String, Tool> toolMap = new HashMap<>();
        for (Tool tool : tools) {
            toolMap.put(tool.getName(), tool);
        }
        return new SdkAgent(providerRegistry, toolMap, sdkConfig, systemPrompt, eventBus);
    }

    /**
     * Returns the list of tools registered with this SDK instance.
     */
    public List<Tool> tools() {
        return tools;
    }

    /**
     * Returns the shared event bus for this SDK instance.
     */
    EventBus eventBus() {
        return eventBus;
    }

    /**
     * Closes all resources held by this SDK instance, including the provider registry.
     */
    @Override
    public void close() {
        if (providerRegistry != null) {
            try {
                providerRegistry.close();
            } catch (Exception ignored) {
                // best-effort close
            }
        }
    }
}
