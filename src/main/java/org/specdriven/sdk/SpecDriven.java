package org.specdriven.sdk;

import org.specdriven.agent.agent.LlmProviderRegistry;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.question.*;
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
    private final DeliveryMode deliveryModeOverride;
    private volatile QuestionDeliveryService deliveryService;

    SpecDriven(LlmProviderRegistry providerRegistry,
               List<Tool> tools,
               String systemPrompt,
               SdkConfig sdkConfig,
               Map<String, String> configMap,
               EventBus eventBus,
               DeliveryMode deliveryModeOverride) {
        this.providerRegistry = providerRegistry;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.sdkConfig = sdkConfig;
        this.configMap = configMap;
        this.eventBus = eventBus;
        this.deliveryModeOverride = deliveryModeOverride;
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
        return new SdkAgent(providerRegistry, toolMap, sdkConfig, systemPrompt,
                eventBus, deliveryModeOverride, deliveryService());
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
     * Returns the question delivery service, initializing lazily on first access.
     * Returns null if initialization fails (e.g., Lealone not available).
     */
    QuestionDeliveryService deliveryService() {
        if (deliveryService == null) {
            synchronized (this) {
                if (deliveryService == null) {
                    try {
                        QuestionRuntime questionRuntime = new QuestionRuntime(eventBus);
                        QuestionStore questionStore = new LealoneQuestionStore(eventBus,
                                "jdbc:lealone:embed:agent_db");
                        questionRuntime.setQuestionStore(questionStore);
                        QuestionDeliveryChannel channel = new LoggingDeliveryChannel();
                        QuestionReplyCollector collector = new InMemoryReplyCollector(questionRuntime);
                        deliveryService = new QuestionDeliveryService(
                                channel, collector, questionRuntime, questionStore);
                    } catch (Exception e) {
                        // Delivery service is optional — agent runs fine without it
                        return null;
                    }
                }
            }
        }
        return deliveryService;
    }

    /**
     * Returns the global delivery mode override, or null if not set.
     */
    DeliveryMode deliveryModeOverride() {
        return deliveryModeOverride;
    }

    /**
     * Closes all resources held by this SDK instance, including the provider registry.
     */
    @Override
    public void close() {
        QuestionDeliveryService ds = deliveryService;
        if (ds != null) {
            try { ds.channel().close(); } catch (Exception ignored) {}
            try { ds.collector().close(); } catch (Exception ignored) {}
        }
        if (providerRegistry != null) {
            try { providerRegistry.close(); } catch (Exception ignored) {}
        }
    }
}
